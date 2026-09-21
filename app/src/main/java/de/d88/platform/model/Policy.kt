package de.d88.platform.model

import de.d88.platform.model.Capability.ADB
import de.d88.platform.model.Capability.APP_INSTALL
import de.d88.platform.model.Capability.APP_REMOVE
import de.d88.platform.model.Capability.APP_START
import de.d88.platform.model.Capability.APP_STOP
import de.d88.platform.model.Capability.DEBUG
import de.d88.platform.model.Capability.FILE_READ
import de.d88.platform.model.Capability.FILE_WRITE
import de.d88.platform.model.Capability.NETWORK
import de.d88.platform.model.Capability.RAG_QUERY
import de.d88.platform.model.Capability.REMOTE_AGENT
import de.d88.platform.model.Capability.SHELL

/**
 * D88-Sicherheitspolicy (versioniert).
 *
 * Defaults sind bewusst lokal-first und deny-by-default:
 *  - NETWORK = DENY
 *  - kritische Kategorien immer Human Gate
 *  - GAST nur in isoliertem GUEST-Bereich
 */
data class Policy(
    val version: Int,
    /** Standard-Netzwerkzustand. Spec: NETWORK = DENY. */
    val networkDefault: NetworkState = NetworkState.DENY,
    /** Selbst ADMIN braucht Freigabe für CRITICAL. Spec Abschnitt 2. */
    val adminCriticalRequiresHumanGate: Boolean = true,
    /** CLIENT: ab dieser Risikoklasse ist ein Human Gate erforderlich. */
    val clientGateFromRisk: RiskClass = RiskClass.MEDIUM,
    /** Datenbereiche, die GAST-Geräte maximal nutzen dürfen. */
    val guestAllowedDataScopes: Set<DataScope> = setOf(DataScope.GUEST),
    /** Capabilities, die GAST niemals erhält (Spec Abschnitt 4). */
    val guestDeniedCapabilities: Set<Capability> = setOf(
        ADB, SHELL, DEBUG, REMOTE_AGENT, APP_INSTALL, APP_REMOVE, APP_START, APP_STOP,
        NETWORK
    ),
    /** Capabilities, die GAST standardmäßig nutzen darf (isolierte Verarbeitung). */
    val guestAllowedCapabilities: Set<Capability> = setOf(
        FILE_READ, FILE_WRITE, RAG_QUERY, de.d88.platform.model.Capability.DEVICE_INFO
    )
) {
    companion object {
        /** Aktuelle Standardpolicy des Prototyps. */
        val CURRENT = Policy(version = 1)
    }
}
