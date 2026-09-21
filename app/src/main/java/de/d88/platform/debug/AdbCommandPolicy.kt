package de.d88.platform.debug

import de.d88.platform.model.Capability
import de.d88.platform.model.Role
import de.d88.platform.model.RiskClass

/**
 * ADB Command Policy (Modul debug/AdbCommandPolicy, Spec Abschnitt 7).
 *
 * Der Agent darf niemals direkt beliebige ADB-Kommandos erzeugen und ausführen.
 * Jedes Kommando durchläuft diese Policy:
 *
 *   Agent → Capability Request → ADB Command Policy → Policy Engine →
 *   Human Gate → Debug Gateway → ADB Adapter → Device
 *
 * Whitelist-Prinzip: nur explizit erkannte Kommandomuster sind erlaubt;
 * Alles andere ist DENIED (Fail-Closed).
 */
object AdbCommandPolicy {

    enum class Verdict {
        /** Read-only, niedriges Risiko – innerhalb einer gültigen Kette ausführbar. */
        ALLOWED,

        /** Ändernd/risikobehaftet – zusätzlich Human Gate erforderlich. */
        REQUIRES_GATE,

        /** Nicht im D88-Whitelist – strikt abgelehnt. */
        DENIED
    }

    data class CheckResult(
        val verdict: Verdict,
        val risk: RiskClass,
        val reason: String
    )

    private data class Pattern(val regex: Regex, val verdict: Verdict, val risk: RiskClass)

    private val patterns = listOf(
        // Read-only Discovery
        Pattern(Regex("""^devices$"""), Verdict.ALLOWED, RiskClass.LOW),
        Pattern(Regex("""^devices -l$"""), Verdict.ALLOWED, RiskClass.LOW),
        Pattern(Regex("""^version$"""), Verdict.ALLOWED, RiskClass.LOW),
        // Read-only Shell-Auslesungen
        Pattern(Regex("""^shell getprop \S+$"""), Verdict.ALLOWED, RiskClass.LOW),
        Pattern(Regex("""^shell dumpsys (battery|window|package) .*$"""), Verdict.ALLOWED, RiskClass.LOW),
        Pattern(Regex("""^logcat -d .*$"""), Verdict.ALLOWED, RiskClass.MEDIUM),
        // Ändernde Kommandos – immer Human Gate
        Pattern(Regex("""^install .*\.apk$"""), Verdict.REQUIRES_GATE, RiskClass.HIGH),
        Pattern(Regex("""^uninstall \S+$"""), Verdict.REQUIRES_GATE, RiskClass.HIGH),
        Pattern(Regex("""^shell am (start|force-stop) .*$"""), Verdict.REQUIRES_GATE, RiskClass.MEDIUM),
        Pattern(Regex("""^shell pm (clear|disable) \S+$"""), Verdict.REQUIRES_GATE, RiskClass.HIGH),
        // Debug
        Pattern(Regex("""^tcpip \d+$"""), Verdict.REQUIRES_GATE, RiskClass.MEDIUM),
        Pattern(Regex("""^usb$"""), Verdict.REQUIRES_GATE, RiskClass.MEDIUM),
        Pattern(Regex("""^wait-for-device$"""), Verdict.ALLOWED, RiskClass.LOW)
    )

    /**
     * Bewertet ein ADB-Kommando.
     * [role] und [capability] werden zusätzlich geprüft: ADB-Fähigkeit ist
     * Voraussetzung (Capability ADB im Grant), ADMIN braucht CONTROL-Scope.
     */
    fun evaluate(role: Role, capability: Capability?, command: String): CheckResult {
        if (capability != Capability.ADB) {
            return CheckResult(Verdict.DENIED, RiskClass.HIGH, "Capability ADB fehlt im Grant")
        }
        if (role == Role.GAST) {
            return CheckResult(Verdict.DENIED, RiskClass.HIGH, "GAST hat niemals ADB-Rechte")
        }
        val normalized = command.trim()
        for (pattern in patterns) {
            if (pattern.regex.matches(normalized)) {
                return CheckResult(pattern.verdict, pattern.risk, "Whitelist-Einstieg: ${pattern.regex.pattern}")
            }
        }
        return CheckResult(
            Verdict.DENIED,
            RiskClass.HIGH,
            "Kommando ist nicht in der D88-ADB-Whitelist (Fail-Closed)"
        )
    }
}
