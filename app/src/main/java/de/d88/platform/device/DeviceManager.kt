package de.d88.platform.device

import de.d88.platform.core.AuditTrail
import de.d88.platform.core.TrustStateMachine
import de.d88.platform.model.AuditEventType
import de.d88.platform.model.Clock
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.Role
import de.d88.platform.model.TrustState

/**
 * Device Manager (Modul device/DeviceManager, Spec Abschnitt 19).
 *
 * Verwaltungshandlungen (Rolle ändern, Vertrauen widerrufen, sperren, entfernen).
 * WICHTIG (Spec Abschnitt 16): Diese Änderungen selbst werden auditiert.
 */
class DeviceManager(
    private val registry: DeviceRegistry,
    private val audit: AuditTrail,
    private val clock: Clock = Clock.SYSTEM
) {

    /** Rolle eines Geräts ändern – nur mit TRUSTED/verwaltungsberechtigtem Kontext sinnvoll; wird auditiert. */
    fun changeRole(deviceId: String, newRole: Role): Result<DeviceIdentity> = runCatching {
        val device = requireExisting(deviceId)
        val oldRole = device.role
        val updated = device.copy(role = newRole).copyUpdated(clock::now)
        registry.update(updated)
        audit.record(
            AuditEventType.DEVICE_ROLE_CHANGED,
            deviceId = deviceId,
            result = "$oldRole → $newRole"
        )
        updated
    }

    fun setTrustState(deviceId: String, newState: TrustState): Result<DeviceIdentity> = runCatching {
        val device = requireExisting(deviceId)
        TrustStateMachine.transition(device.trustState, newState)
        val updated = device.copy(trustState = newState).copyUpdated(clock::now)
        registry.update(updated)
        audit.record(
            AuditEventType.DEVICE_TRUST_CHANGED,
            deviceId = deviceId,
            result = "${device.trustState} → $newState"
        )
        updated
    }

    fun suspend(deviceId: String): Result<DeviceIdentity> = setTrustState(deviceId, TrustState.SUSPENDED)

    fun revoke(deviceId: String): Result<DeviceIdentity> = setTrustState(deviceId, TrustState.REVOKED)

    fun block(deviceId: String): Result<DeviceIdentity> = setTrustState(deviceId, TrustState.BLOCKED)

    /** Gerät aus der Registry entfernen (endgültig, auditiert). */
    fun remove(deviceId: String): Result<DeviceIdentity> = runCatching {
        val device = requireExisting(deviceId)
        registry.remove(deviceId)
        audit.record(AuditEventType.DEVICE_REMOVED, deviceId = deviceId, result = "entfernt")
        device
    }

    /** Neues Gerät registrieren (Startzustand DISCOVERED – Verbindung ≠ Vertrauen). */
    fun discover(device: DeviceIdentity): DeviceIdentity {
        val normalized = device.copy(
            trustState = if (device.trustState == TrustState.TRUSTED) {
                // Neuentdeckte Geräte starten nie direkt als TRUSTED.
                TrustState.DISCOVERED
            } else device.trustState
        )
        val registered = registry.register(normalized)
        audit.record(
            AuditEventType.DEVICE_REGISTERED,
            deviceId = registered.deviceId,
            result = "Rolle ${registered.role}, Trust ${registered.trustState}"
        )
        return registered
    }

    private fun requireExisting(deviceId: String): DeviceIdentity =
        registry.find(deviceId)
            ?: error("Gerät '$deviceId' existiert nicht in der D88-Registry")
}
