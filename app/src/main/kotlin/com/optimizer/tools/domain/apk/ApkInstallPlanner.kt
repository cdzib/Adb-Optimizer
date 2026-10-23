package com.optimizer.tools.domain.apk

import java.io.File

data class ApkDescriptor(
    val file: File,
    val packageName: String,
    val splitName: String?,
)

data class ApkInstallGroup(
    val packageName: String,
    val files: List<File>,
    val installMultiple: Boolean,
)

object ApkInstallPlanner {
    fun create(apks: List<ApkDescriptor>): List<ApkInstallGroup> {
        require(apks.isNotEmpty()) { "Selecciona al menos un archivo APK." }
        require(apks.all { it.packageName.isNotBlank() }) { "No se pudo identificar el paquete de uno de los APK." }

        return apks.groupBy(ApkDescriptor::packageName).map { (packageName, packageApks) ->
            if (packageApks.size > 1) {
                val baseApks = packageApks.count { it.splitName == null }
                require(baseApks == 1) {
                    "Los APK divididos de $packageName deben incluir exactamente un APK base."
                }
                val splitNames = packageApks.mapNotNull(ApkDescriptor::splitName)
                require(splitNames.distinct().size == splitNames.size) {
                    "La selección contiene APK dividido duplicado para $packageName."
                }
            } else {
                require(packageApks.single().splitName == null) {
                    "Falta el APK base de ${packageApks.single().packageName}; selecciona el conjunto completo."
                }
            }
            ApkInstallGroup(
                packageName = packageName,
                files = packageApks.map(ApkDescriptor::file),
                installMultiple = packageApks.size > 1,
            )
        }
    }
}
