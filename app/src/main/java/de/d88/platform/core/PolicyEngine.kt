package de.d88.platform.core

import de.d88.platform.model.ActionRequest
import de.d88.platform.model.AgentSession
import de.d88.platform.model.Capability
import de.d88.platform.model.DataFlow
import de.d88.platform.model.DeviceIdentity
import de.d88.platform.model.NetworkState
import de.d88.platform.model.Policy
import de.d88.platform.model.RiskClass
import de.d88.platform.model.Role

/**
 * Policy Engine (Modul authorization/PolicyEngine, Spec Abschnitt 19).
 *
 * Verantwortlich für:
 *  1. Risiko-Eskalation (kritische Kategorien werden immer CRITICAL)
 *  2. Netzwerk-Deny-by-Default (Spec Abschnitt 12: NETWORK = DENY)
 *  3. Rollenabhängige Human-Gate-Pflichten
 */
class PolicyEngine(private val policy: Policy = Policy.CURRENT) {

    data class Evaluation(
        val finalRisk: RiskClass,
        val violations: List<String>,
        val requiresHumanGate: Boolean,
        val notes: List<String>
    )

    /** Eskaliertes Risiko der Aktion. */
    fun escalateRisk(request: ActionRequest): RiskClass {
        if (request.category.isCriticalByDefinition) return RiskClass.CRITICAL
        var risk = request.riskClass
        // Netzwerkbezug ohne explizite Freigabe ist mindestens HIGH.
        if (request.networkFlow && !risk.atLeast(RiskClass.HIGH)) {
            risk = RiskClass.HIGH
        }
        // Sensible Capabilities.
        if (request.capability in sensitiveCapabilities && !risk.atLeast(RiskClass.HIGH)) {
            risk = RiskClass.HIGH
        }
        // Externer Datenfluss ist mindestens HIGH.
        if (request.dataFlow == DataFlow.EXTERNAL && !risk.atLeast(RiskClass.HIGH)) {
            risk = RiskClass.HIGH
        }
        return risk
    }

    /**
     * Vollständige Policy-Auswertung.
     * [violations] enthalten Harte Verletzungen → Kette bricht mit DENIED.
     */
    fun evaluate(
        request: ActionRequest,
        device: DeviceIdentity,
        session: AgentSession
    ): Evaluation {
        val notes = mutableListOf<String>()
        val violations = mutableListOf<String>()
        val finalRisk = escalateRisk(request)

        // --- Netzwerk-Deny-by-Default (Spec: NETWORK = DENY) ---
        if (request.networkFlow) {
            val granted = session.networkState == NetworkState.GRANTED &&
                device.networkState == NetworkState.GRANTED
            if (!granted) {
                violations += "Netzwerkzusage fehlt: NETWORK ist standardmäßig DENY " +
                    "(keine stillen Verbindungen, Telemetrie oder Cloud)"
            } else {
                notes += "Netzwerk-Grant für diese Session/Gerät vorhanden"
            }
        }

        // --- GAST-Bereich: nichts über GUEST/Gastbereich hinaus ---
        if (device.role == Role.GAST) {
            if (request.dataFlow != DataFlow.LOCAL_ONLY) {
                violations += "GAST: nur lokale Datenverarbeitung erlaubt"
            }
            if (finalRisk.atLeast(RiskClass.CRITICAL)) {
                violations += "GAST: kritische Aktion im Gastbereich ist ausgeschlossen"
            }
        }

        // --- Human-Gate-Pflichten ---
        var gate = false
        when (device.role) {
            Role.ADMIN -> if (finalRisk == RiskClass.CRITICAL && policy.adminCriticalRequiresHumanGate) {
                gate = true
                notes += "ADMIN: kritische Aktionen bleiben Human-in-the-Loop"
            }
            Role.CLIENT -> if (finalRisk.atLeast(policy.clientGateFromRisk)) {
                gate = true
                notes += "CLIENT: Risiko ${finalRisk.name} erfordert Freigabe"
            }
            Role.GAST -> if (finalRisk.atLeast(RiskClass.HIGH)) {
                gate = true
                notes += "GAST: erhöhte Risikoklasse erfordert Freigabe"
            }
        }

        return Evaluation(finalRisk, violations, gate, notes)
    }

    companion object {
        private val sensitiveCapabilities = setOf(
            Capability.ADB, Capability.SHELL, Capability.DEBUG,
            Capability.APP_INSTALL, Capability.APP_REMOVE,
            Capability.NETWORK, Capability.WLAN, Capability.BLE
        )
    }
}
