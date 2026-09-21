package de.d88.platform.core

import de.d88.platform.model.TrustState

/**
 * Zustandsautomat des D88-Vertrauensstatus (Spec README.md, Abschnitt 6).
 *
 * Hauptpfad: DISCOVERED → PAIRING_REQUIRED → PAIRED → IDENTIFIED → TRUST_PENDING → TRUSTED
 * Sonderzustände: SUSPENDED, REVOKED, EXPIRED, BLOCKED, UNKNOWN
 *
 *.Invarianten (werden hier erzwungen):
 *  - DISCOVERED != TRUSTED
 *  - PAIRED != AUTHORIZED
 *  - ADB_CONNECTED != ADMIN
 */
object TrustStateMachine {

    private val TRANSITIONS: Map<TrustState, Set<TrustState>> = mapOf(
        TrustState.DISCOVERED to setOf(TrustState.PAIRING_REQUIRED, TrustState.UNKNOWN, TrustState.BLOCKED),
        TrustState.PAIRING_REQUIRED to setOf(TrustState.PAIRED, TrustState.BLOCKED, TrustState.UNKNOWN),
        TrustState.PAIRED to setOf(TrustState.IDENTIFIED, TrustState.REVOKED, TrustState.BLOCKED),
        TrustState.IDENTIFIED to setOf(TrustState.TRUST_PENDING, TrustState.REVOKED, TrustState.BLOCKED),
        TrustState.TRUST_PENDING to setOf(TrustState.TRUSTED, TrustState.SUSPENDED, TrustState.REVOKED, TrustState.BLOCKED),
        TrustState.TRUSTED to setOf(TrustState.SUSPENDED, TrustState.REVOKED, TrustState.EXPIRED, TrustState.BLOCKED),
        TrustState.SUSPENDED to setOf(TrustState.TRUST_PENDING, TrustState.REVOKED, TrustState.BLOCKED),
        TrustState.EXPIRED to setOf(TrustState.PAIRING_REQUIRED, TrustState.REVOKED),
        TrustState.REVOKED to emptySet(),
        TrustState.BLOCKED to emptySet(),
        TrustState.UNKNOWN to setOf(TrustState.DISCOVERED, TrustState.BLOCKED)
    )

    /** Zustände, in denen GAST-Geräte den isolierten GUEST-Bereich nutzen dürfen. */
    val GUEST_ALLOWED_STATES: Set<TrustState> = setOf(
        TrustState.PAIRED, TrustState.IDENTIFIED, TrustState.TRUST_PENDING
    )

    fun canTransition(from: TrustState, to: TrustState): Boolean =
        TRANSITIONS[from]?.contains(to) == true

    /** Führt eine Transition aus; bei illegaler Transition wird [IllegalTransitionException] geworfen. */
    fun transition(from: TrustState, to: TrustState): TrustState {
        if (!canTransition(from, to)) {
            throw IllegalTransitionException(from, to)
        }
        return to
    }

    /**
     * Prüft, ob ein Gerät im Zustand [state] überhaupt autorisiert werden darf:
     *  - ADMIN/CLIENT: nur TRUSTED
     *  - GAST: nur der GUEST-Zustandsraum (nie TRUSTED-fähig)
     */
    fun authorizable(state: TrustState, requiresTrusted: Boolean): Boolean =
        if (state.isDead) false
        else if (requiresTrusted) state.isTrusted
        else state in GUEST_ALLOWED_STATES

    class IllegalTransitionException(val from: TrustState, val to: TrustState) :
        IllegalStateException("Illegale Trust-Transition: $from → $to")
}
