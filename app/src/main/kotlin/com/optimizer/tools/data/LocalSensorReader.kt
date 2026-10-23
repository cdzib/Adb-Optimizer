package com.optimizer.tools.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dev.adboptimizer.core.model.DeviceSensorInfo

/** Sensor inventory only; it never registers a listener or samples values. */
internal class LocalSensorReader(
    private val context: Context,
) {
    fun readSensors(): List<DeviceSensorInfo> =
        runCatching {
            context
                .getSystemService(SensorManager::class.java)
                ?.getSensorList(Sensor.TYPE_ALL)
                ?.map {
                    DeviceSensorInfo(
                        name = it.name,
                        vendor = it.vendor,
                        typeName = it.stringType,
                        typeCode = it.type,
                        version = it.version,
                    )
                }?.distinctBy { it.name to it.typeCode }
                ?.sortedWith(compareBy<DeviceSensorInfo> { it.typeName.orEmpty() }.thenBy { it.name })
                .orEmpty()
        }.getOrDefault(emptyList())
}
