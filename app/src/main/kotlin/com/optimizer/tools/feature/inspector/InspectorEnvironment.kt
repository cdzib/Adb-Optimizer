package com.optimizer.tools.feature.inspector

/** Puertos del inspector de apps. */
interface InspectorEnvironment {
    fun hasDevice(): Boolean

    suspend fun inspect(packageName: String): AppInspection
}
