package de.d88.platform.model

/**
 * Vertrauensstatus eines D88-Geräts (Spec README.md, Abschnitt 6).
 *
 * Kerninvarianten:
 *  - DISCOVERED != TRUSTED
 *  - PAIRED != AUTHORIZED
 *  - ADB_CONNECTED != ADMIN
 */
enum class TrustState {
    DISCOVERED,
    PAIRING_REQUIRED,
    PAIRED,
    IDENTIFIED,
    TRUST_PENDING,
    TRUSTED,

    SUSPENDED,
    REVOKED,
    EXPIRED,
    BLOCKED,
    UNKNOWN;

    /** Nur TRUSTED verleiht volle Gerätefunktionen. */
    val isTrusted: Boolean
        get() = this == TRUSTED

    /** Zustände, in denen (nahezu) keine Autorisierung mehr möglich ist. */
    val isDead: Boolean
        get() = this == REVOKED || this == BLOCKED
}
