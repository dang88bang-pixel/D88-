package de.d88.platform.agent

import de.d88.platform.core.AuditTrail
import de.d88.platform.device.DeviceRegistry
import de.d88.platform.model.AgentSession
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.Capability
import de.d88.platform.model.Clock
import de.d88.platform.model.DataScope
import de.d88.platform.model.NetworkState
import de.d88.platform.model.Policy
import de.d88.platform.model.Role
import de.d88.platform.model.SessionAuthState

/**
 * Verwaltung von Agent-Sessions (Spec Abschnitt 10: Session-basierte Rechte).
 *
 * Jede Session erhält einen eigenen Security Context. Der Grant ist zeitlich
 * begrenzt (endsAt) und verfällt mit dem Session-Ende.
 */
class AgentSessionManager(
    private val registry: DeviceRegistry,
    private val audit: AuditTrail,
    private val clock: Clock = Clock.SYSTEM,
    private val defaultTtlMs: Long = DEFAULT_TTL_MS
) {

    fun createSession(
        deviceId: String,
        projectId: String?,
        taskId: String?,
        capabilities: Set<Capability>,
        dataScope: DataScope,
        networkState: NetworkState = NetworkState.DENY,
        ttlMs: Long = defaultTtlMs
    ): Result<AgentSession> = runCatching {
        val device = registry.find(deviceId)
            ?: error("Gerät '$deviceId' unbekannt – keine Session möglich")

        // CLIENT benötigt Projektbindung; GAST erhält nur GUEST-Bereich.
        when (device.role) {
            Role.CLIENT -> require(projectId != null) { "CLIENT-Session benötigt eine Projektbindung" }
            Role.GAST -> {
                require(projectId == null && taskId == null) { "GAST-Session ohne Projekt-/Aufgabenbindung" }
                require(dataScope == DataScope.GUEST) { "GAST-Session nur mit GUEST-Datenbereich" }
            }
            Role.ADMIN -> Unit
        }

        // Session-Capabilities dürfen nie über die Geräte-Capabilities hinausgehen.
        val unknown = capabilities.filter { it !in device.capabilities }
        require(unknown.isEmpty()) {
            "Folgende Capabilities fehlen am Gerät: ${unknown.joinToString()}"
        }

        val now = clock.now()
        val session = AgentSession(
            sessionId = "sess-${now.toString(16)}-${deviceId.hashCode().toUInt().toString(16)}",
            deviceId = deviceId,
            role = device.role,
            projectId = projectId,
            taskId = taskId,
            capabilities = capabilities,
            dataScope = dataScope,
            networkState = networkState,
            deviceScope = deviceId,
            policyVersion = Policy.CURRENT.version,
            authorizationState = SessionAuthState.ACTIVE,
            humanGateState = de.d88.platform.model.HumanGateState.NOT_REQUIRED,
            startedAt = now,
            endsAt = now + ttlMs
        )
        registry.registerSession(session)
        audit.record(
            AuditEventType.SESSION_CREATED,
            deviceId = deviceId,
            sessionId = session.sessionId,
            projectId = projectId,
            taskId = taskId,
            result = "Capabilities: ${capabilities.joinToString()}; TTL ${ttlMs / 1000}s"
        )
        session
    }

    /** Session beenden → Grant verfällt sofort. */
    fun endSession(sessionId: String, reason: String = "manuell beendet"): Result<Unit> = runCatching {
        val session = registry.findSession(sessionId) ?: error("Session '$sessionId' nicht gefunden")
        registry.endSession(sessionId)
        audit.record(
            AuditEventType.SESSION_ENDED,
            deviceId = session.deviceId,
            sessionId = sessionId,
            projectId = session.projectId,
            taskId = session.taskId,
            result = reason
        )
    }
}
