package com.arsys.netatmo.service

import com.arsys.netatmo.data.repository.ApiResult

/**
 * Pure diagnostic wording for an action's Netatmo API result. It only describes the result:
 * the engine ignores API errors (no failure, no retry), exactly as in v195.
 */
object ApiResultLog {
    fun isError(result: ApiResult<*>): Boolean = result is ApiResult.Error

    fun describe(what: String, result: ApiResult<*>): String =
        if (result is ApiResult.Error) {
            "$what: ERROR de Netatmo código=${result.code ?: "-"} ${result.message}. NO se reintenta (igual que antes); la acción NO se aplicó"
        } else {
            "$what: OK (Netatmo aceptó la orden)"
        }
}
