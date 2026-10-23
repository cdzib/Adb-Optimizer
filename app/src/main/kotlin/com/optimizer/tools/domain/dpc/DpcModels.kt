package com.optimizer.tools.domain.dpc

enum class DpcMode { DEVICE, PROFILE }

enum class DpcCheckStatus { OK, WARN, FAIL }

data class DpcCheck(
    val id: String,
    val label: String,
    val status: DpcCheckStatus,
    val detail: String,
)

data class DpcPreflight(
    val sdk: Int,
    val owners: List<String>,
    val users: List<DpcUser>,
    val accounts: Int,
    val checks: List<DpcCheck>,
    val ready: Boolean,
)

data class DpcUser(
    val id: Int,
    val name: String,
)

data class DpcReceiver(
    val component: String,
    val packageName: String,
)

data class DpcResult(
    val ok: Boolean,
    val message: String,
)
