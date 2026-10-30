package com.arsys.netatmo.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkTransitionLogTest {

    @Test
    fun `first state of a work is logged`() {
        val log = WorkTransitionLog()
        val line = log.next("inmediata", "ENQUEUED", 0, "sin parada")
        assertTrue(line!!.contains("inmediata"))
        assertTrue(line.contains("ENQUEUED"))
        assertTrue(line.contains("intento 1"))
    }

    @Test
    fun `repeated identical state is not logged again`() {
        val log = WorkTransitionLog()
        log.next("inmediata", "ENQUEUED", 0, "sin parada")
        assertNull(log.next("inmediata", "ENQUEUED", 0, "sin parada"))
    }

    @Test
    fun `state change is logged`() {
        val log = WorkTransitionLog()
        log.next("inmediata", "ENQUEUED", 0, "sin parada")
        val line = log.next("inmediata", "RUNNING", 0, "sin parada")
        assertTrue(line!!.contains("RUNNING"))
    }

    @Test
    fun `new attempt in the same state is logged`() {
        val log = WorkTransitionLog()
        log.next("inmediata", "ENQUEUED", 0, "sin parada")
        val line = log.next("inmediata", "ENQUEUED", 1, "sin parada")
        assertTrue(line!!.contains("intento 2"))
    }

    @Test
    fun `stop reason change is logged`() {
        val log = WorkTransitionLog()
        log.next("inmediata", "RUNNING", 0, "sin parada")
        val line = log.next("inmediata", "RUNNING", 0, "cuota agotada")
        assertTrue(line!!.contains("cuota agotada"))
    }

    @Test
    fun `works are tracked independently`() {
        val log = WorkTransitionLog()
        log.next("inmediata", "ENQUEUED", 0, "sin parada")
        val line = log.next("periódica", "ENQUEUED", 0, "sin parada")
        assertTrue(line!!.contains("periódica"))
        assertNull(log.next("inmediata", "ENQUEUED", 0, "sin parada"))
    }

    @Test
    fun `failure with attempts then success are both logged`() {
        val log = WorkTransitionLog()
        log.next("inmediata", "ENQUEUED", 0, "sin parada")
        assertEquals(true, log.next("inmediata", "FAILED", 1, "sin parada")!!.contains("FAILED"))
        assertEquals(true, log.next("inmediata", "SUCCEEDED", 2, "sin parada")!!.contains("SUCCEEDED"))
    }
}
