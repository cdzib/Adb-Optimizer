package com.optimizer.tools.data

import android.os.Build
import dev.adboptimizer.core.model.DeviceNetworkInterface
import java.net.NetworkInterface

/** Active interfaces and their addresses, read through the public network API. */
internal class LocalNetworkReader {
    fun readInterfaces(): List<DeviceNetworkInterface> =
        runCatching {
            NetworkInterface
                .getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .mapNotNull { network -> readInterface(network) }
                .sortedBy { it.name }
        }.getOrDefault(emptyList())

    private fun readInterface(network: NetworkInterface): DeviceNetworkInterface? =
        runCatching {
            val active =
                network.isUp &&
                    !network.isLoopback &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || !network.isVirtual)
            if (!active) return null
            val addresses =
                network.interfaceAddresses
                    .mapNotNull { entry ->
                        val address = entry.address?.hostAddress?.substringBefore('%') ?: return@mapNotNull null
                        if (entry.networkPrefixLength > 0) "$address/${entry.networkPrefixLength}" else address
                    }.distinct()
                    .sorted()
            if (addresses.isEmpty()) null else DeviceNetworkInterface(network.name, addresses)
        }.getOrNull()
}
