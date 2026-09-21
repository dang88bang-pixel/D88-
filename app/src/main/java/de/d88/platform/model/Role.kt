package de.d88.platform.model

/**
 * Die drei privaten D88-Geräterollen (Spec README.md, Abschnitt 2–4).
 *
 * Eine Rolle definiert nur die GUNDLAGE. Die tatsächlich nutzbare Berechtigung
 * entsteht erst aus: Role + Trust + Capability + Project + Task + Session + Policy.
 */
enum class Role {
    /** Persönliches Haupt- bzw. Administrationsgerät. Kritische Aktionen bleiben Human-in-the-Loop. */
    ADMIN,

    /** Privates Gerät zur aktiven Nutzung. Explizit kein halber ADMIN. */
    CLIENT,

    /** Gerät außerhalb des vertrauenswürdigen Verbunds – isolierter privater Arbeitsbereich. */
    GAST;

    /**
     * Rollen, die für Vollnutzung (ADMIN/CLIENT-Rechte) den Zustand TRUSTED erfordern.
     * GAST bleibt bewusst NICHT trusted-fähig – sie erhält nur den isolierten GUEST-Bereich.
     */
    val requiresTrustedState: Boolean
        get() = this != GAST
}
