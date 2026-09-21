package de.d88.platform.core

import de.d88.platform.model.AgentSession
import de.d88.platform.model.Capability
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.Role

/**
 * Capability Engine (Modul authorization/CapabilityEngine, Spec Abschnitt 19).
 *
 * Capabilities werden getrennt von der Rolle verwaltet. Die effektive Berechtigung
 * ist die Schnittmenge aus Geräte-Capabilities, Session-Capabilities und Policy.
 */
object CapabilityEngine {

    /**
     * Prüft, ob eine Capability in diesem Kontext wirksam ist.
     * Rückgabe: null = OK, sonst Begründung der Verletzung.
     */
    fun check(
        device: DeviceIdentity,
        session: AgentSession,
        capability: Capability,
        policyGuestDenied: Set<Capability>
    ): String? {
        if (device.role == Role.GAST && capability in policyGuestDenied) {
            return "Capability $capability ist für GAST standardmäßig verboten"
        }
        if (capability !in device.capabilities) {
            return "Gerät besitzt Capability $capability nicht"
        }
        if (capability !in session.capabilities) {
            return "Session-Grant enthält Capability $capability nicht (Session-Grant prüfen)"
        }
        return null
    }
}
