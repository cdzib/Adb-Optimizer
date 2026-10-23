package com.optimizer.tools

import android.app.Application
import com.optimizer.tools.data.AppContainer

/**
 * El contenedor vive a nivel de proceso: la sesion ADB y las instalaciones
 * en curso no dependen del ciclo de vida de la Activity.
 */
class AdbOptimizerApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
