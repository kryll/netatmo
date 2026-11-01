package com.arsys.netatmo.ui.screens.automations.advanced

import org.junit.Assert.assertEquals
import org.junit.Test

class ListReorderTest {

    private val base = listOf("a", "b", "c")

    @Test
    fun `middle item moves up`() {
        assertEquals(listOf("b", "a", "c"), base.movedWithin(1, -1))
    }

    @Test
    fun `middle item moves down`() {
        assertEquals(listOf("a", "c", "b"), base.movedWithin(1, 1))
    }

    @Test
    fun `first item cannot move up`() {
        assertEquals(base, base.movedWithin(0, -1))
    }

    @Test
    fun `last item cannot move down`() {
        assertEquals(base, base.movedWithin(2, 1))
    }

    @Test
    fun `out of range index returns the same list`() {
        assertEquals(base, base.movedWithin(5, 1))
        assertEquals(base, base.movedWithin(-1, 1))
    }

    @Test
    fun `single element list never changes`() {
        assertEquals(listOf("a"), listOf("a").movedWithin(0, 1))
        assertEquals(listOf("a"), listOf("a").movedWithin(0, -1))
    }

    @Test
    fun `order is preserved after move`() {
        assertEquals(listOf("b", "c", "a"), base.movedWithin(0, 2))
    }
}
