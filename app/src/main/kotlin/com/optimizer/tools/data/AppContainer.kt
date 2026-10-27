package com.optimizer.tools.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.SystemClock
import com.optimizer.tools.data.dpc.ApkDownloader
import com.optimizer.tools.data.dpc.DpcRepository
import com.optimizer.tools.data.logging.LogcatAppLogger
import com.optimizer.tools.data.logging.NoOpAppLogger
import com.optimizer.tools.data.optimization.AssetCatalogSource
import com.optimizer.tools.data.optimization.CatalogBundle
import com.optimizer.tools.data.optimization.CatalogLoader
import com.optimizer.tools.data.optimization.ChangeJournal
import com.optimizer.tools.data.remote.RemoteAppRepository
import com.optimizer.tools.data.remote.RemoteLauncherRepository
import com.optimizer.tools.domain.apk.ApkDescriptor
import com.optimizer.tools.domain.apk.ApkInstallPlanner
import com.optimizer.tools.domain.apk.InstallOptions
import com.optimizer.tools.domain.logging.AppLogger
import com.optimizer.tools.domain.model.KnownDevice
import com.optimizer.tools.domain.model.deduplicateKnownDevices
import com.optimizer.tools.domain.port.ApkInstaller
import com.optimizer.tools.domain.usecase.InstallHostAppsUseCase
import dev.adboptimizer.core.adb.AdbConnectionFactory
import dev.adboptimizer.core.adb.AdbDiscovery
import dev.adboptimizer.core.adb.AdbKeys
import dev.adboptimizer.core.adb.DeviceRepository
import dev.adboptimizer.core.adb.RemoteApkInstallGroup
import dev.adboptimizer.core.adb.RemoteApkInstallOutcome
import dev.adboptimizer.core.adb.scrcpy.ScrcpyUnit
import dev.adboptimizer.core.adb.scrcpy.ScrcpyVideoStream
import dev.adboptimizer.core.model.ActionSpec
import dev.adboptimizer.core.model.DeviceInfo
import dev.adboptimizer.core.model.DeviceMetrics
import dev.adboptimizer.core.model.RemoteLauncher
import dev.adboptimizer.core.model.StepResult
import dev.adboptimizer.core.model.StepStatus
import dev.adboptimizer.core.optimization.RecipeEngine
import dev.adboptimizer.core.shell.AdbShell
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Contenedor manual de dependencias.
 * Se prefiere sobre Hilt en esta etapa para mantener el arranque y los tests simples;
 * ver docs/adr/ADR-008-di-manual.md.
 */
class AppContainer(
    private val context: Context,
) : Closeable,
    ApkInstaller {
    /** Logging centralizado: Logcat en builds depurables y no-op en el resto. */
    val logger: AppLogger by lazy {
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            LogcatAppLogger()
        } else {
            NoOpAppLogger
        }
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    val catalog: CatalogBundle by lazy {
        CatalogLoader(AssetCatalogSource(context.assets)).load()
    }

    val actions: List<ActionSpec> get() = catalog.actions.actions

    val dictionary: Map<String, String> by lazy {
        runCatching {
            json.decodeFromString<Map<String, String>>(
                context.assets
                    .open("i18n/es.json")
                    .bufferedReader()
                    .use { it.readText() },
            )
        }.getOrDefault(emptyMap())
    }

    val journal: ChangeJournal by lazy {
        ChangeJournal(File(context.filesDir, "change-journal.json"))
    }

    val discovery: AdbDiscovery by lazy { AdbDiscovery(context) }

    val wifiStatus: WifiStatusRepository by lazy { WifiStatusRepository(context) }

    val apkInstallManager: ApkInstallManager by lazy { ApkInstallManager(context) }

    fun startSessionService(device: DeviceInfo) {
        AdbSessionService.start(context, device.displayName)
    }

    fun stopSessionService() {
        AdbSessionService.stop(context)
    }

    fun refreshSessionNotification() {
        sessionStore.activeDevice()?.let(::startSessionService)
    }

    private fun setLastConnected(address: String) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_DEVICE, address)
            .apply()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    val shizukuLocalAccess: ShizukuLocalAccess by lazy { ShizukuLocalAccess(context) }

    private val localShellDelegate = lazy { ShizukuAdbShell(context, shizukuLocalAccess) }
    val localShell: ShizukuAdbShell by localShellDelegate

    val localApps: LocalAppRepository by lazy { LocalAppRepository(context, shizukuLocalAccess, localShell) }

    val localDevice: LocalDeviceRepository by lazy { LocalDeviceRepository(context) }

    val localEngine: RecipeEngine get() = RecipeEngine(localShell, journal)

    suspend fun runLocalAction(
        action: ActionSpec,
        packageName: String,
    ): StepResult {
        val device = localDevice.readInfo()
        val minSdk = action.minSdk
        if (minSdk != null && device.sdk < minSdk) {
            return StepResult(
                index = 0,
                run = action.run,
                status = StepStatus.UNSUPPORTED,
                error = "Esta acción requiere Android $minSdk o posterior.",
            )
        }
        return localEngine.runAction(action, device, packageName)
    }

    /** Repositorio de apps del dispositivo conectado, o null si no hay sesion ADB. */
    fun remoteApps(): RemoteAppRepository? = shell?.let { RemoteAppRepository(it) }

    private val keys by lazy { AdbKeys.load(context) }

    val sessionStore = AppSessionStore()

    val shell: AdbShell? get() = sessionStore.activeShell()

    val device: DeviceInfo? get() = sessionStore.activeDevice()

    val activeSession: DeviceSession? get() = sessionStore.activeSession()

    fun connectedDevices(): List<DeviceInfo> = sessionStore.state.value.devices

    fun selectDevice(deviceId: String) {
        if (sessionStore.select(deviceId)) setLastConnected(deviceId)
    }

    fun disconnectDevice(deviceId: String): Boolean {
        val removed = sessionStore.remove(deviceId)
        if (removed) {
            sessionStore.activeId()?.let(::setLastConnected) ?: clearActiveSession()
            if (sessionStore.isEmpty()) stopSessionService()
        }
        return removed
    }

    val engine: RecipeEngine?
        get() = shell?.let { RecipeEngine(it, journal) }

    suspend fun metrics(): DeviceMetrics? = shell?.let { DeviceRepository(it).readMetrics() }

    /** Apps instaladas por el usuario (no sistema). */
    suspend fun userPackages(): List<String> {
        val adb = shell ?: return emptyList()
        return adb
            .exec("pm list packages -3", 25_000)
            .stdout
            .lineSequence()
            .mapNotNull { line ->
                line.trim().removePrefix("package:").takeIf { it.isNotBlank() && !it.contains(' ') }
            }.distinct()
            .sorted()
            .toList()
    }

    suspend fun installApks(
        uris: List<Uri>,
        options: InstallOptions = InstallOptions(),
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> =
        withContext(Dispatchers.IO) {
            val stagedFiles = mutableListOf<File>()
            try {
                val descriptors =
                    uris.mapIndexed { index, uri ->
                        onProgress("Preparando APK ${index + 1}/${uris.size}")
                        val file = File.createTempFile("adboptimizer-", ".apk", context.cacheDir)
                        stagedFiles += file
                        val copiedBytes =
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                file.outputStream().use(input::copyTo)
                            } ?: throw IOException("No se pudo abrir uno de los archivos seleccionados.")
                        if (copiedBytes == 0L) throw IOException("Uno de los APK seleccionados está vacío.")

                        @Suppress("DEPRECATION")
                        val packageInfo =
                            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                                ?: throw IOException("Uno de los archivos no es un APK válido.")
                        val splitName = packageInfo.splitNames?.singleOrNull()
                        ApkDescriptor(file, packageInfo.packageName.orEmpty(), splitName)
                    }
                installDescriptors(descriptors, options, onProgress)
            } finally {
                stagedFiles.forEach { it.delete() }
            }
        }

    /** Copia un contenido (Uri) a la cache local para checksum o envio. */
    suspend fun copyToCache(
        uri: Uri,
        prefix: String,
    ): File =
        withContext(Dispatchers.IO) {
            val target = File.createTempFile("$prefix-", ".apk", context.cacheDir)
            val copied =
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use(input::copyTo)
                } ?: throw IOException("No se pudo abrir el archivo seleccionado.")
            if (copied == 0L) throw IOException("El APK seleccionado está vacío.")
            target
        }

    /** Instala archivos APK ya descargados (p. ej. un DPC desde URL). */
    suspend fun installApkFiles(
        files: List<File>,
        options: InstallOptions = InstallOptions(),
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> =
        withContext(Dispatchers.IO) {
            val descriptors =
                files.mapIndexed { index, file ->
                    onProgress("Preparando APK ${index + 1}/${files.size}")
                    if (!file.isFile || file.length() == 0L) throw IOException("El APK ${file.name} está vacío.")
                    val info = packageInfoFor(file)
                    ApkDescriptor(file, info.packageName.orEmpty(), info.splitNames?.singleOrNull())
                }
            installDescriptors(descriptors, options, onProgress)
        }

    @Suppress("DEPRECATION")
    private fun packageInfoFor(file: File): android.content.pm.PackageInfo =
        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("El archivo no es un APK válido.")

    val dpc: DpcRepository?
        get() = sessionStore.activeShell()?.let { DpcRepository(it) }

    val logcat: LogcatRepository?
        get() = sessionStore.activeShell()?.let { LogcatRepository(it) }

    val settingsDb: SettingsDbRepository?
        get() = sessionStore.activeShell()?.let { SettingsDbRepository(it) }

    val appInspector: AppInspectorRepository?
        get() = sessionStore.activeShell()?.let { AppInspectorRepository(it) }

    val doze: DozeRepository?
        get() = sessionStore.activeShell()?.let { DozeRepository(it) }

    val apkDownloader: ApkDownloader by lazy { ApkDownloader(context) }

    val installHostAppsUseCase: InstallHostAppsUseCase by lazy {
        InstallHostAppsUseCase(hostApps = localApps, installer = this)
    }

    suspend fun remoteHomeLaunchers(): List<RemoteLauncher> = RemoteLauncherRepository(requireRemoteShell()).listHomeLaunchers()

    suspend fun currentRemoteHomeLauncher(): RemoteLauncher? = RemoteLauncherRepository(requireRemoteShell()).currentHomeLauncher()

    suspend fun setRemoteHomeLauncher(packageName: String): RemoteLauncher =
        RemoteLauncherRepository(requireRemoteShell()).setDefaultHomeLauncher(packageName)

    /** Abre un stream de video scrcpy con el servidor copiado desde los assets a caché. */
    suspend fun openScrcpyVideo(onLog: (String) -> Unit): ScrcpyVideoStream =
        withContext(Dispatchers.IO) {
            val adb = requireRemoteShell()
            val jar =
                File(context.cacheDir, "scrcpy-server.jar").also { file ->
                    context.assets.open("scrcpy/scrcpy-server.jar").use { input ->
                        file.outputStream().use(input::copyTo)
                    }
                }
            val scid = (System.nanoTime() and 0x7FFF_FFFF).toInt()
            AdbConnectionFactory.openScrcpyVideo(adb, jar, scid, onLog)
        }

    suspend fun probeScrcpyVideo(onLog: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            openScrcpyVideo(onLog).use { stream ->
                var packets = 0
                var keyFrames = 0
                var bytes = 0L
                var resizes = 0
                while (packets < PROBE_PACKET_LIMIT) {
                    when (val unit = stream.readUnit()) {
                        is ScrcpyUnit.Packet -> {
                            packets++
                            if (unit.isKeyFrame) keyFrames++
                            bytes += unit.payload.size
                        }

                        is ScrcpyUnit.Resize -> {
                            resizes++
                        }
                    }
                }
                "Video ${stream.info.codec} ${stream.info.width}x${stream.info.height} desde ${stream.info.deviceName}: " +
                    "$packets paquetes, $keyFrames fotogramas clave, $bytes bytes, $resizes cambios de tamaño."
            }
        }

    private fun requireRemoteShell(): AdbShell = shell ?: throw IOException("No hay un dispositivo remoto conectado.")

    override suspend fun installDescriptors(
        descriptors: List<ApkDescriptor>,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> = installDescriptors(descriptors, InstallOptions(), onProgress)

    private suspend fun installDescriptors(
        descriptors: List<ApkDescriptor>,
        options: InstallOptions,
        onProgress: (String) -> Unit,
    ): List<RemoteApkInstallOutcome> {
        val adb = shell ?: throw IOException("No hay un dispositivo remoto conectado.")
        val groups = ApkInstallPlanner.create(descriptors)
        return AdbConnectionFactory.installApkGroups(
            shell = adb,
            groups =
                groups.map { group ->
                    RemoteApkInstallGroup(
                        packageName = group.packageName,
                        files = group.files,
                        installMultiple = group.installMultiple,
                        flags = options.flags(),
                    )
                },
            onProgress,
        )
    }

    /**
     * @param secure true para `adb-tls-connect` (Android 11+ ya vinculado),
     *               false para ADB clasico en el puerto indicado.
     */
    suspend fun connect(
        host: String,
        port: Int,
        secure: Boolean,
    ): DeviceInfo {
        val startedAt = SystemClock.elapsedRealtime()
        debugAdb("Connect requested: host=$host port=$port secure=$secure")
        // AppSessionStore.add reemplaza solo la sesion del mismo dispositivo.
        val adb =
            AdbConnectionFactory.createShell(keys, host, port, secure) { message, error ->
                debugAdb(message, error)
            }
        val identityKey: String?
        val info =
            try {
                val deviceInfo = DeviceRepository(adb).readInfo("$host:$port")
                identityKey = readDeviceIdentity(adb)
                deviceInfo
            } catch (error: Exception) {
                debugAdb("ADB connection or initial device info read failed for $host:$port", error)
                (adb as? java.io.Closeable)?.close()
                throw error
            }
        sessionStore.add(DeviceSession("$host:$port", info, adb))
        rememberDevice(
            KnownDevice(
                host,
                port,
                secure,
                info.displayName,
                identityKey = identityKey,
                isTv = info.isTv,
                manufacturer = info.manufacturer,
                androidVersion = info.androidVersion,
                sdk = info.sdk,
                abi = info.abi,
            ),
        )
        setLastConnected("$host:$port")
        debugAdb("Connect completed: ${info.displayName} at $host:$port in ${SystemClock.elapsedRealtime() - startedAt}ms")
        return info
    }

    /** Vincula con codigo de 6 digitos o con el password de un QR. */
    suspend fun pair(
        host: String,
        port: Int,
        password: String,
    ): Result<Unit> {
        val session = AdbConnectionFactory.createPairing(context, keys)
        return try {
            session.pair(host, port, password)
        } finally {
            session.close()
        }
    }

    // ---------------------------------------------------------------- recientes

    @Synchronized
    fun knownDevices(): List<KnownDevice> {
        val stored =
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_DEVICES, null)
                ?: return emptyList()
        val devices =
            runCatching {
                json.decodeFromString(ListSerializer(KnownDevice.serializer()), stored)
            }.getOrDefault(emptyList())
        val unique = deduplicateKnownDevices(devices)
        if (unique != devices) saveKnownDevices(unique)
        return unique
    }

    @Synchronized
    private fun rememberDevice(entry: KnownDevice) {
        saveKnownDevices(deduplicateKnownDevices(listOf(entry) + knownDevices()).take(8))
    }

    private fun saveKnownDevices(devices: List<KnownDevice>) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICES, json.encodeToString(ListSerializer(KnownDevice.serializer()), devices))
            .apply()
    }

    /** Store only a local fingerprint, never the raw Android identifier or a log of it. */
    private suspend fun readDeviceIdentity(adb: AdbShell): String? =
        try {
            val result = adb.exec("settings get secure android_id", timeoutMs = 1_500L)
            val value = result.stdout.trim()
            if (result.exitCode == 0 && value.matches(Regex("[0-9a-fA-F]{16}")) && value.any { it != '0' }) {
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.lowercase().toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
            } else {
                null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    fun lastConnectedAddress(): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_DEVICE, null)

    fun clearActiveSession() {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_DEVICE)
            .apply()
    }

    @Synchronized
    fun forgetDevice(address: String) {
        val updated = knownDevices().filterNot { it.address == address }
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICES, json.encodeToString(ListSerializer(KnownDevice.serializer()), updated))
            .apply()
    }

    fun disconnect() {
        sessionStore.clear()
    }

    override fun close() {
        disconnect()
        if (localShellDelegate.isInitialized()) localShell.close()
    }

    private fun debugAdb(
        message: String,
        error: Throwable? = null,
    ) {
        logger.debug("AdbConnection", message, error)
    }

    private companion object {
        const val PREFS = "adb-known-devices"
        const val KEY_DEVICES = "devices"
        const val KEY_LAST_DEVICE = "last-device"
        const val PROBE_PACKET_LIMIT = 120
    }
}
