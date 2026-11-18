package com.arsys.netatmo.ui.screens.home

import com.arsys.netatmo.data.repository.ApiResult
import com.arsys.netatmo.data.repository.AuthRepository
import com.arsys.netatmo.data.repository.ThermostatRepository
import com.arsys.netatmo.domain.usecase.GetThermostatStateUseCase
import com.arsys.netatmo.domain.usecase.SetModeUseCase
import com.arsys.netatmo.domain.usecase.SetTemperatureUseCase
import com.arsys.netatmo.service.BoostManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Verifies the ViewModel-to-repository path of the away mode: exact home_id, mode and
 * endtime reach setHomeMode, and a past end time never reaches the API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AwayModeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var getThermostatState: GetThermostatStateUseCase
    private lateinit var setTemperature: SetTemperatureUseCase
    private lateinit var setMode: SetModeUseCase
    private lateinit var authRepository: AuthRepository
    private lateinit var repository: ThermostatRepository
    private lateinit var boostManager: BoostManager
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        getThermostatState = mockk()
        setTemperature = mockk()
        setMode = mockk()
        authRepository = mockk()
        repository = mockk()
        boostManager = mockk()
        every { authRepository.selectedHomeId } returns MutableStateFlow("home-1")
        every { boostManager.boostState } returns MutableStateFlow(null)
        every { boostManager.remainingMinutes } returns MutableStateFlow(0)
        coEvery { getThermostatState(any()) } returns ApiResult.Error("sin datos en test")
        coEvery { repository.setHomeMode(any(), any(), any()) } returns ApiResult.Success(Unit)
        viewModel = HomeViewModel(
            getThermostatState, setTemperature, setMode, authRepository, repository, boostManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setAwayMode_withEndTime_passesExactHomeModeAndEndTime() = runTest {
        val end = System.currentTimeMillis() / 1000 + 7200
        viewModel.setAwayMode(end)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.setHomeMode("home-1", "away", end) }
        assertTrue(viewModel.uiState.value.successMessage?.startsWith("Modo Ausente activado hasta el") == true)
    }

    @Test
    fun setAwayMode_withoutEndTime_passesNullEndTime() = runTest {
        viewModel.setAwayMode(null)
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.setHomeMode("home-1", "away", null) }
        assertEquals("Modo Ausente activado hasta que lo cambies", viewModel.uiState.value.successMessage)
    }

    @Test
    fun setAwayMode_pastEndTime_neverReachesApi() = runTest {
        val past = System.currentTimeMillis() / 1000 - 60
        viewModel.setAwayMode(past)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.setHomeMode(any(), any(), any()) }
        assertEquals("La fecha y hora debe ser posterior a ahora", viewModel.uiState.value.error)
    }

    @Test
    fun returnToSchedule_restoresScheduleMode() = runTest {
        viewModel.returnToSchedule()
        advanceUntilIdle()
        coVerify(exactly = 1) { repository.setHomeMode("home-1", "schedule", null) }
        assertEquals("Modo Programado restaurado", viewModel.uiState.value.successMessage)
    }
}
