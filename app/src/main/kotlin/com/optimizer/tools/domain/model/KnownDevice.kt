package com.optimizer.tools.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class KnownDevice(
    val host: String,
    val port: Int,
    val secure: Boolean,
    val model: String,
    val identityKey: String? = null,
    val isTv: Boolean? = null,
    val manufacturer: String? = null,
    val androidVersion: String? = null,
    val sdk: Int? = null,
    val abi: String? = null,
) {
    val address: String get() = "$host:$port"
    val displayName: String get() = model.ifBlank { host }

    fun isSameDevice(other: KnownDevice): Boolean {
        if (!identityKey.isNullOrBlank() && !other.identityKey.isNullOrBlank()) {
            return identityKey == other.identityKey
        }
        return host.trim().equals(other.host.trim(), ignoreCase = true) &&
            (address == other.address || model.trim().equals(other.model.trim(), ignoreCase = true))
    }
}

/** Input is ordered newest first. Never use a model name alone as device identity. */
fun deduplicateKnownDevices(devices: List<KnownDevice>): List<KnownDevice> =
    devices.fold(emptyList()) { unique, device ->
        if (unique.any { it.isSameDevice(device) }) unique else unique + device
    }
