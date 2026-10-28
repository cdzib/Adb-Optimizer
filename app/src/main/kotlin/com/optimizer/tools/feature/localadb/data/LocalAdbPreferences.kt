package com.optimizer.tools.feature.localadb.data

import android.content.Context

/** Recuerda que el dispositivo ya autorizó la llave RSA de ADB local. */
class LocalAdbPreferences(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isAuthorized(): Boolean = preferences.getBoolean(KEY_AUTHORIZED, false)

    fun markAuthorized() {
        preferences.edit().putBoolean(KEY_AUTHORIZED, true).apply()
    }

    fun clearAuthorized() {
        preferences.edit().remove(KEY_AUTHORIZED).apply()
    }

    private companion object {
        const val FILE = "local-adb"
        const val KEY_AUTHORIZED = "authorized"
    }
}
