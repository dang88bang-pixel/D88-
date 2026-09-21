package de.d88.platform.model

/** Entschluss der Autorisierungskette. */
enum class Decision {
    /** Ausführung direkt zulässig (niedriges Risiko, alle Ebenen bestanden). */
    APPROVED,

    /** Nicht zulässig – Gründe und Stufe sind im Trace nachvollziehbar. */
    DENIED,

    /** Nur mit menschlicher Freigabe (Human Gate) auszuführen. */
    HUMAN_GATE_REQUIRED
}

/** Ergebnis einer Stufe der 13-stufigen Autorisierungskette. */
data class StageOutcome(
    val stage: Stage,
    val outcome: String,
    val detail: String
)

/**
 * Die Stufen der D88-Autorisierungskette (Spec README.md, Abschnitt 18).
 * Der Agent darf niemals eine Ebene überspringen – die Kette läuft immer vollständig
 * durch, bis sie entweder entscheidet oder an einer Stufe scheitert.
 */
enum class Stage {
    DEVICE,
    IDENTITY,
    TRUST,
    ROLE,
    PROJECT,
    TASK,
    SESSION,
    CAPABILITY,
    POLICY,
    HUMAN_GATE,
    EXECUTION,
    VALIDATION,
    AUDIT
}

/**
 * Vollständiges Ergebnis einer Kette – Grundlage für Audit und UI.
 */
data class AuthorizationResult(
    val actionId: String,
    val decision: Decision,
    val finalRisk: RiskClass,
    val stoppedAt: Stage?,
    val stageTrace: List<StageOutcome>,
    val policyVersion: Int,
    val deviceId: String?,
    val decidedAt: Long
) {
    /** Gründe = alle Details der durchlaufenen Stufen (für Human Gate UI und Audit). */
    val reasons: List<String>
        get() = stageTrace.map { "${it.stage.name}: ${it.detail}" }
}
