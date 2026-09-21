package de.d88.platform.model

/**
 * Datenzugriffsbereiche (Spec README.md, Abschnitt 11).
 * D88 trennt strikt: GLOBAL / PROJECT / TASK / SESSION / DEVICE / PRIVATE / GUEST.
 */
enum class DataScope {
    GLOBAL,
    PROJECT,
    TASK,
    SESSION,
    DEVICE,
    PRIVATE,
    GUEST
}

/** Netzwerkzustand einer Session bzw. eines Geräts. Standard: DENY. */
enum class NetworkState {
    /** Default. Keine stillen Verbindungen, keine Telemetrie, keine Cloud. */
    DENY,

    /** Explizit für einen Ziel-/Zweck-Scope freigegeben (nur über Policy + Freigabe). */
    GRANTED
}

/** Debug-/ADB-Rechte (eigener Scope, nie implizit). */
enum class DebugScope {
    NONE,
    READ_ONLY,
    CONTROL
}

/** Zustand der Pairing-Verbindung. */
enum class PairingState {
    NOT_PAIRED,
    PENDING,
    PAIRED,
    FAILED
}

/** Zustand der Authentifizierung. */
enum class AuthState {
    UNAUTHENTICATED,
    CHALLENGED,
    AUTHENTICATED,
    EXPIRED
}
