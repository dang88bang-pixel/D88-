package de.d88.platform.model

/**
 * Device Capabilities (Spec README.md, Abschnitt 8).
 *
 * Capabilities werden getrennt von der Rolle verwaltet. ADB/DEBUG sind dabei
 * lediglich Transport-/Steuerkanäle und niemals selbst die Autorisierung.
 */
enum class Capability {
    DEVICE_INFO,
    APP_INSTALL,
    APP_REMOVE,
    APP_START,
    APP_STOP,
    LOG_READ,
    SCREEN_CAPTURE,
    SCREEN_STREAM,
    CAMERA,
    USB,
    USB_STORAGE,
    USB_SERIAL,
    BLE,
    WLAN,
    NETWORK,
    FILE_READ,
    FILE_WRITE,
    ADB,
    SHELL,
    DEBUG,
    SANDBOX,
    REMOTE_AGENT,
    /** Lokale Wissensabfrage (RAG/Knowledge) – immer lokal, ohne Netzwerk. */
    RAG_QUERY
}
