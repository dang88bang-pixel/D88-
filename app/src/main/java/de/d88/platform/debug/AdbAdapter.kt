package de.d88.platform.debug

/**
 * ADB-/Debug-Transport (Spec README.md, Abschnitt 7).
 *
 * ADB ist lediglich ein Transport-/Steuerkanal und niemals selbst die Autorisierung.
 * Ein Adapter meldet ehrlich, ob ein echter Transport verfügbar ist –
 * vorgetäuschte Erfolgsmeldungen sind in D88 verboten (Abnahmerichtlinie).
 */

enum class TransportState {
    /** Echter Transport vorhanden (z. B. adb-Binary nutzbar). */
    AVAILABLE,

    /** Kein Transport in dieser Umgebung – Aktionen werden NICHT ausgeführt. */
    UNAVAILABLE,

    /** Verbindung wird aufgebaut. */
    CONNECTING,

    /** Transportfehler. */
    ERROR
}

data class AdbResult(
    val deviceId: String,
    val command: String,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val executedAt: Long,
    /** true nur, wenn wirklich auf einem echten Transport ausgeführt. */
    val realTransport: Boolean
)

/**
 * Interface für echte Transport-Adapter (ADB/USB/BLE/WLAN/VNC folgen).
 * Adapter melden Verfügbarkeit selbst – Simulation ist ein eigener,
 * klar gekennzeichneter Modus (kein Adapter).
 */
interface AdbAdapter {
    val name: String
    fun state(): TransportState
    fun execute(deviceId: String, command: String): AdbResult
}
