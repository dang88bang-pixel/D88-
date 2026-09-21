package de.d88.platform.model

/** Risikoklasse einer Aktion (deklariert vom Agenten, eskaliert von der Policy Engine). */
enum class RiskClass {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    fun atLeast(other: RiskClass): Boolean = this.ordinal >= other.ordinal
}

/**
 * Aktionskategorien, mit denen die Policy Engine kritische Aktionen
 * unabhängig von Rolle und Deklaration erkennen kann
 * (Spec README.md, Abschnitt 2 „Wichtige Einschränkung“).
 */
enum class ActionCategory {
    READ,
    ANALYZE,
    EXECUTE,
    APP_LIFECYCLE,
    DEBUG,
    CONFIG_CHANGE,
    DATA_DELETE,
    DATA_EXPORT,
    FIRMWARE,
    NETWORK_ENABLE,
    CAPABILITY_INSTALL,
    MCP_CONNECT,
    PHYSICAL,
    UNKNOWN_FUNCTION;

    /**
     * Kritische Kategorien nach D88-Spec – diese werden IMMER auf CRITICAL
     * eskaliert und erfordern ein Human Gate, unabhängig von der Rolle.
     */
    val isCriticalByDefinition: Boolean
        get() = when (this) {
            CONFIG_CHANGE,
            DATA_DELETE,
            DATA_EXPORT,
            FIRMWARE,
            NETWORK_ENABLE,
            CAPABILITY_INSTALL,
            MCP_CONNECT,
            PHYSICAL,
            UNKNOWN_FUNCTION -> true
            else -> false
        }
}
