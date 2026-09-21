package de.d88.platform.model

/**
 * Session-basierte Rechte (Spec README.md, Abschnitt 10).
 * Jede Agent Session erhält einen eigenen Security Context.
 * Eine Berechtigung kann nur für eine bestimmte Session gelten und verfällt mit ihr.
 */
data class AgentSession(
    val sessionId: String,
    val deviceId: String,
    val role: Role,
    val projectId: String?,
    val taskId: String?,
    val capabilities: Set<Capability>,
    val dataScope: DataScope,
    val networkState: NetworkState,
    val deviceScope: String,
    val policyVersion: Int,
    var authorizationState: SessionAuthState,
    var humanGateState: HumanGateState,
    val startedAt: Long,
    /** Ende des Grants – danach ist die Session für Berechtigungen nicht mehr gültig. */
    val endsAt: Long?
) {
    fun isGranted(now: Long): Boolean =
        authorizationState == SessionAuthState.ACTIVE &&
            (endsAt == null || now <= endsAt)
}

enum class SessionAuthState {
    ACTIVE,
    ENDED,
    REVOKED,
    EXPIRED
}

enum class HumanGateState {
    NOT_REQUIRED,
    PENDING,
    APPROVED,
    DENIED
}
