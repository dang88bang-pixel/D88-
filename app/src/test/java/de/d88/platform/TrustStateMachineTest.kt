package de.d88.platform

import de.d88.platform.core.TrustStateMachine
import de.d88.platform.model.Role
import de.d88.platform.model.TrustState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TrustStateMachineTest {

    @Test
    fun `main path is legal`() {
        var state = TrustState.DISCOVERED
        for (next in listOf(
            TrustState.PAIRING_REQUIRED,
            TrustState.PAIRED,
            TrustState.IDENTIFIED,
            TrustState.TRUST_PENDING,
            TrustState.TRUSTED
        )) {
            state = TrustStateMachine.transition(state, next)
        }
        assertEquals(TrustState.TRUSTED, state)
    }

    @Test
    fun `DISCOVERED can never jump to TRUSTED`() {
        assertFalse(TrustStateMachine.canTransition(TrustState.DISCOVERED, TrustState.TRUSTED))
        try {
            TrustStateMachine.transition(TrustState.DISCOVERED, TrustState.TRUSTED)
            fail("Expected IllegalTransitionException")
        } catch (e: TrustStateMachine.IllegalTransitionException) {
            // erwartet
        }
    }

    @Test
    fun `PAIRED is not trusted`() {
        assertFalse(TrustStateMachine.authorizable(TrustState.PAIRED, requiresTrusted = true))
    }

    @Test
    fun `TRUSTED authorizable for admin and client`() {
        assertTrue(TrustStateMachine.authorizable(TrustState.TRUSTED, Role.ADMIN.requiresTrustedState))
        assertTrue(TrustStateMachine.authorizable(TrustState.TRUSTED, Role.CLIENT.requiresTrustedState))
    }

    @Test
    fun `TRUST_PENDING is NOT authorizable for trusted roles`() {
        assertFalse(TrustStateMachine.authorizable(TrustState.TRUST_PENDING, requiresTrusted = true))
    }

    @Test
    fun `guest states authorizable for gast role`() {
        assertTrue(TrustStateMachine.authorizable(TrustState.TRUST_PENDING, requiresTrusted = false))
        assertTrue(TrustStateMachine.authorizable(TrustState.PAIRED, requiresTrusted = false))
        assertFalse(TrustStateMachine.authorizable(TrustState.DISCOVERED, requiresTrusted = false))
    }

    @Test
    fun `dead states never authorizable`() {
        for (state in listOf(TrustState.REVOKED, TrustState.BLOCKED)) {
            assertFalse(TrustStateMachine.authorizable(state, requiresTrusted = true))
            assertFalse(TrustStateMachine.authorizable(state, requiresTrusted = false))
        }
    }

    @Test
    fun `REVOKED is terminal`() {
        for (next in TrustState.values()) {
            assertFalse("REVOKED → $next sollte illegal sein",
                TrustStateMachine.canTransition(TrustState.REVOKED, next))
        }
    }

    @Test
    fun `suspended can return to trust pending`() {
        assertTrue(TrustStateMachine.canTransition(TrustState.SUSPENDED, TrustState.TRUST_PENDING))
    }

    @Test
    fun `expired requires re-pairing`() {
        assertTrue(TrustStateMachine.canTransition(TrustState.EXPIRED, TrustState.PAIRING_REQUIRED))
        assertFalse(TrustStateMachine.canTransition(TrustState.EXPIRED, TrustState.TRUSTED))
    }
}
