package dev.adboptimizer.core.adb

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import dev.adboptimizer.core.shell.AdbErrorKind
import dev.adboptimizer.core.shell.AdbException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.GeneralSecurityException

data class DiscoveredService(
    val host: String,
    val port: Int,
    val serviceName: String = "",
) {
    val address: String get() = "$host:$port"
}

/**
 * Descubrimiento mDNS de los servicios de ADB inalambrico.
 *
 * - [PAIRING] aparece cuando el dispositivo esta en modo vinculacion.
 * - [CONNECT] es el puerto TLS al que hay que conectarse.
 *
 * Es el equivalente en Android a `adb mdns services` del escritorio.
 */
class AdbDiscovery(
    private val context: Context,
) {
    fun observe(serviceType: String): Flow<DiscoveredService> =
        callbackFlow {
            val appContext = context.applicationContext
            val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager == null) {
                close(IllegalStateException("Android NSD service is unavailable"))
                awaitClose { }
                return@callbackFlow
            }

            val nsdServiceType = "_$serviceType._tcp."
            debugLog("Starting NSD discovery for $nsdServiceType")
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val multicastLock =
                wifi?.createMulticastLock("adb-optimizer-mdns")?.apply {
                    setReferenceCounted(false)
                    runCatching { acquire() }
                }

            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(registeredType: String) {
                        debugLog("NSD discovery started: $registeredType")
                    }

                    override fun onServiceFound(service: NsdServiceInfo) {
                        val foundType = service.serviceType?.trimEnd('.')
                        if (foundType != nsdServiceType.trimEnd('.')) {
                            debugLog("Ignoring unrelated NSD service ${service.serviceName} type=$foundType")
                            return
                        }
                        debugLog("NSD service found: ${service.serviceName} type=$foundType; resolving")
                        try {
                            @Suppress("DEPRECATION")
                            nsdManager.resolveService(
                                service,
                                object : NsdManager.ResolveListener {
                                    override fun onResolveFailed(
                                        serviceInfo: NsdServiceInfo,
                                        errorCode: Int,
                                    ) {
                                        debugLog("NSD resolve failed for ${serviceInfo.serviceName}: code=$errorCode")
                                    }

                                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                        val host = serviceInfo.host?.hostAddress
                                        val port = serviceInfo.port
                                        if (host.isNullOrBlank() || port !in 1..65535) {
                                            debugLog("Resolved NSD service has invalid endpoint: host=$host port=$port")
                                            return
                                        }
                                        val result =
                                            DiscoveredService(
                                                host = host.removePrefix("/"),
                                                port = port,
                                                serviceName = serviceInfo.serviceName.orEmpty(),
                                            )
                                        debugLog(
                                            "Resolved NSD endpoint: requestedType=$nsdServiceType " +
                                                "resolvedType=${serviceInfo.serviceType} name=${result.serviceName} " +
                                                "host=${result.host} port=${result.port}",
                                        )
                                        trySend(result)
                                    }
                                },
                            )
                        } catch (error: Exception) {
                            debugLog("Could not resolve NSD service ${service.serviceName}", error)
                        }
                    }

                    override fun onServiceLost(service: NsdServiceInfo) {
                        debugLog("NSD service lost: ${service.serviceName} type=${service.serviceType}")
                    }

                    override fun onDiscoveryStopped(registeredType: String) {
                        debugLog("NSD discovery stopped: $registeredType")
                    }

                    override fun onStartDiscoveryFailed(
                        registeredType: String,
                        errorCode: Int,
                    ) {
                        val error = IllegalStateException("NSD discovery start failed: type=$registeredType code=$errorCode")
                        debugLog(error.message ?: "NSD discovery start failed", error)
                        close(error)
                    }

                    override fun onStopDiscoveryFailed(
                        registeredType: String,
                        errorCode: Int,
                    ) {
                        val error = IllegalStateException("NSD discovery stop failed: type=$registeredType code=$errorCode")
                        debugLog(error.message ?: "NSD discovery stop failed", error)
                        close(error)
                    }
                }

            try {
                nsdManager.discoverServices(nsdServiceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (error: Exception) {
                debugLog("Could not start NSD discovery for $nsdServiceType", error)
                close(error)
            }
            awaitClose {
                runCatching { nsdManager.stopServiceDiscovery(listener) }
                multicastLock?.takeIf { it.isHeld }?.release()
                debugLog("Released NSD discovery for $nsdServiceType")
            }
        }

    companion object {
        private const val TAG = "AdbDiscovery"

        const val PAIRING = "adb-tls-pairing"
        const val CONNECT = "adb-tls-connect"
    }

    private fun debugLog(
        message: String,
        error: Throwable? = null,
    ) {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            if (error == null) Log.d(TAG, message) else Log.e(TAG, message, error)
        }
    }
}

/** Vinculacion (pairing) SPAKE2 con codigo de 6 digitos o con el password de un QR. */
class AdbPairing(
    private val context: Context,
) {
    internal suspend fun pair(
        manager: AdbManager,
        host: String,
        port: Int,
        password: String,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                manager.setHostAddress(host)
                manager.setApi(30)
                val paired = manager.pair(host, port, password)
                if (!paired) {
                    throw AdbException(
                        "El dispositivo rechazo la vinculacion. Verifica el codigo o el QR.",
                        AdbErrorKind.PAIRING,
                    )
                }
                if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.d("AdbPairing", "Pairing succeeded for $host:$port")
                }
            }.recoverCatching { error ->
                if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                    Log.e("AdbPairing", "Pairing failed for $host:$port", error)
                }
                throw when (error) {
                    is AdbException -> {
                        error
                    }

                    is AdbPairingRequiredException -> {
                        AdbException("El dispositivo pide vinculacion.", AdbErrorKind.PAIRING, error)
                    }

                    is GeneralSecurityException -> {
                        AdbException("Fallo el intercambio seguro de vinculacion.", AdbErrorKind.PAIRING, error)
                    }

                    is IOException -> {
                        AdbException(
                            error.message ?: "No se pudo vincular con el dispositivo.",
                            AdbErrorKind.OFFLINE,
                            error,
                        )
                    }

                    else -> {
                        AdbException(
                            error.message ?: "Error desconocido al vincular.",
                            AdbErrorKind.PAIRING,
                            error,
                        )
                    }
                }
            }
        }
}
