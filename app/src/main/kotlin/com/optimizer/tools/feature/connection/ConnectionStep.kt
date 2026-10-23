package com.optimizer.tools.feature.connection

enum class ConnectionStep {
    SELECT,
    CONNECT,
    ;

    fun next(): ConnectionStep =
        when (this) {
            SELECT, CONNECT -> CONNECT
        }

    fun previous(): ConnectionStep? =
        when (this) {
            SELECT -> null
            CONNECT -> SELECT
        }
}
