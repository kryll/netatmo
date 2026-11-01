package com.arsys.netatmo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomationChainTest {

    @Test
    fun `follow extends the chain for a new target`() {
        val next = AutomationChain.follow(setOf(1L), 2L)
        assertEquals(setOf(1L, 2L), next)
    }

    @Test
    fun `follow refuses a direct self reference`() {
        assertNull(AutomationChain.follow(setOf(1L), 1L))
    }

    @Test
    fun `follow refuses an indirect cycle`() {
        // A -> B -> A: when B tries to run A, A is already in the chain.
        assertNull(AutomationChain.follow(setOf(1L, 2L), 1L))
        assertNull(AutomationChain.follow(setOf(1L, 2L), 2L))
    }

    @Test
    fun `follow refuses chains at the depth limit`() {
        val chain = (1L..AutomationChain.MAX_DEPTH.toLong()).toSet()
        assertNull(AutomationChain.follow(chain, 999L))
    }

    @Test
    fun `follow allows a chain one below the depth limit`() {
        val chain = (1L until AutomationChain.MAX_DEPTH.toLong()).toSet()
        val next = AutomationChain.follow(chain, 999L)
        assertEquals(chain + 999L, next)
        assertEquals(AutomationChain.MAX_DEPTH, next?.size)
    }
}
