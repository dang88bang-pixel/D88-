package de.d88.platform.model

/**
 * Action Request für den Human Gate (Spec README.md, Abschnitt 13).
 * Jede relevante Geräteaktion wird mit diesem strukturierten Request abgebildet.
 */
data class ActionRequest(
    val actionId: String,
    val deviceId: String,
    val role: Role,
    val projectId: String?,
    val taskId: String?,
    val sessionId: String?,
    val capability: Capability,
    val target: String,
    val requestedChange: String,
    val reason: String,
    /** Datenfluss: LOCAL_ONLY | DEVICE_TO_SERVER | SERVER_TO_DEVICE | EXTERNAL */
    val dataFlow: DataFlow,
    /** Möchte die Aktion Netzwerkverbindungen? */
    val networkFlow: Boolean,
    val riskClass: RiskClass,
    val category: ActionCategory,
    val evidence: String?
)

enum class DataFlow {
    LOCAL_ONLY,
    DEVICE_TO_SERVER,
    SERVER_TO_DEVICE,
    EXTERNAL
}
