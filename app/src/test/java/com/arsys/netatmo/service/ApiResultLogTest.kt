package com.arsys.netatmo.service

import com.arsys.netatmo.data.repository.ApiResult
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultLogTest {
    @Test
    fun `api error is reported as error, not applied and not retried`() {
        val result = ApiResult.Error("Error 401", 401)
        assertTrue(ApiResultLog.isError(result))
        val text = ApiResultLog.describe("Cambiar modo", result)
        assertTrue(text.contains("ERROR"))
        assertTrue(text.contains("401"))
        assertTrue(text.contains("NO se reintenta"))
        assertTrue(text.contains("NO se aplicó"))
        assertFalse(text.contains("OK"))
    }

    @Test
    fun `api success is reported as ok`() {
        val result = ApiResult.Success(Unit)
        assertFalse(ApiResultLog.isError(result))
        val text = ApiResultLog.describe("Cambiar modo", result)
        assertTrue(text.contains("OK"))
        assertFalse(text.contains("ERROR"))
    }
}
