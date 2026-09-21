package de.d88.platform.model

/**
 * Typen relevanter Vorgänge für den Causal Audit (Spec README.md, Abschnitt 14).
 */
enum class AuditEventType {
    USER_REQUEST,
    TASK_CREATED,
    PLAN_CREATED,
    CAPABILITY_REQUESTED,
    POLICY_EVALUATED,
    HUMAN_REQUEST,
    HUMAN_APPROVAL,
    HUMAN_DENIAL,
    HUMAN_TIMEOUT,
    DEVICE_ACTION,
    DEVICE_RESULT,
    TRANSPORT_UNAVAILABLE,
    VALIDATION,
    CONTEXT_UPDATE,
    SESSION_CREATED,
    SESSION_ENDED,
    DEVICE_REGISTERED,
    DEVICE_ROLE_CHANGED,
    DEVICE_TRUST_CHANGED,
    DEVICE_SUSPENDED,
    DEVICE_REVOKED,
    DEVICE_BLOCKED,
    DEVICE_REMOVED,
    AUDIT_VERIFIED,
    AUDIT_TAMPERED
}

/**
 * Kausal verknüpftes Audit-Event. Jedes Event verweist auf sein parentEventId
 * und trägt eine kryptografische Kette (hash über Parent-Hash + Inhalt),
 * damit spätere Manipulation nachweisbar ist.
 */
data class AuditEvent(
    val eventId: String,
    val type: AuditEventType,
    val actionId: String?,
    val deviceId: String?,
    val sessionId: String?,
    val projectId: String?,
    val taskId: String?,
    val capability: Capability?,
    val policyDecision: String?,
    val authorization: String?,
    val parentEventId: String?,
    val timestamp: Long,
    val result: String?,
    val evidence: String?,
    val hash: String,
    val parentHash: String?
)
