package com.arsys.netatmo.ui.screens.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.arsys.netatmo.domain.model.ThermostatMode
import com.arsys.netatmo.ui.theme.NetatmoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the away-mode sheet with fake callbacks (no account, no network) and checks
 * the two requested flows: "hasta que lo cambie" (no end time) and a chosen date/time
 * (end time passed to Netatmo, which returns the home to schedule by itself).
 * Each test writes a PNG capture of the sheet dialog into
 * app/build/outputs/compose-shots/ for visual inspection from CI artifacts.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: booting the real NetatmoApp would run Hilt injection, which
// opens EncryptedSharedPreferences (AndroidKeyStore) - not available under Robolectric
// and not needed by this sheet.
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AwaySheetUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun captureSheet(name: String) {
        val dialog = ShadowDialog.getLatestDialog()
        val view = dialog?.window?.decorView ?: error("No dialog to capture for $name")
        assertTrue("dialog has no width for $name", view.width > 0)
        assertTrue("dialog has no height for $name", view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun awaySheet_untilChange_activatesWithoutEndTime() {
        var activated: Long? = -1L // sentinel: -1 = not called
        composeRule.setContent {
            NetatmoTheme {
                AwayBottomSheet(
                    active = false,
                    currentEndTime = null,
                    onDismiss = {},
                    onActivate = { activated = it },
                    onDeactivate = {}
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("away-sheet-until-change")

        composeRule.onNodeWithText("Modo Ausente", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hasta que lo cambie", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Fecha y hora", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Activar modo Ausente", useUnmergedTree = true)
            .performScrollTo().performClick()
        composeRule.waitForIdle()
        assertNull("until-change must activate with no end time", activated)
    }

    @Test
    fun awaySheet_dateTime_activatesWithFutureEndTime() {
        var activated: Long? = null
        var called = false
        composeRule.setContent {
            NetatmoTheme {
                AwayBottomSheet(
                    active = false,
                    currentEndTime = null,
                    onDismiss = {},
                    onActivate = { activated = it; called = true },
                    onDeactivate = {}
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Fecha y hora", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        captureSheet("away-sheet-datetime")

        composeRule.onNodeWithText("Fecha de vuelta", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Hora de vuelta", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()

        // Default target is now + 3h, always in the future.
        composeRule.onNodeWithText("Activar modo Ausente", useUnmergedTree = true)
            .performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue("activate must be called", called)
        assertNotNull(activated)
        assertTrue(
            "end time must be in the future",
            (activated ?: 0L) > System.currentTimeMillis() / 1000
        )
    }

    @Test
    fun awaySheet_active_offersReturnToSchedule() {
        var deactivated = false
        val end = System.currentTimeMillis() / 1000 + 3600
        composeRule.setContent {
            NetatmoTheme {
                AwayBottomSheet(
                    active = true,
                    currentEndTime = end,
                    onDismiss = {},
                    onActivate = {},
                    onDeactivate = { deactivated = true }
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("away-sheet-active")

        composeRule.onNodeWithText("Volver a Programado", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Volver a Programado", useUnmergedTree = true)
            .performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(deactivated)
    }

    @Test
    fun awaySheet_inFrostGuard_warnsReturnIsToSchedule() {
        composeRule.setContent {
            NetatmoTheme {
                AwayBottomSheet(
                    active = false,
                    currentEndTime = null,
                    currentMode = ThermostatMode.FROST_GUARD,
                    onDismiss = {},
                    onActivate = {},
                    onDeactivate = {}
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("away-sheet-frostguard-warning")
        composeRule.onNodeWithText(
            "Ahora la casa está en modo Anticongelación. Al terminar volverá a Programado, no a Anticongelación.",
            useUnmergedTree = true
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun epochSecondsFrom_parsesLocalDateTime() {
        val epoch = epochSecondsFrom("2026-11-20", "18:30")
        assertNotNull(epoch)
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = epoch!! * 1000 }
        assertEquals(2026, cal.get(java.util.Calendar.YEAR))
        assertEquals(10, cal.get(java.util.Calendar.MONTH)) // November = 10
        assertEquals(20, cal.get(java.util.Calendar.DAY_OF_MONTH))
        assertEquals(18, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(java.util.Calendar.MINUTE))
        assertNull(epochSecondsFrom("not-a-date", "18:30"))
    }

    @Test
    fun epochSecondsFrom_rejectsValuesCalendarWouldNormalize() {
        // Feb 30 does not exist: lenient Calendar would roll it into March.
        assertNull(epochSecondsFrom("2026-02-30", "12:00"))
        // 25:00 is not a time of day.
        assertNull(epochSecondsFrom("2026-11-20", "25:00"))
        // Month 13 neither.
        assertNull(epochSecondsFrom("2026-13-10", "10:00"))
    }

    @Test
    fun epochSecondsFrom_rejectsDstGapTime_europeMadrid() {
        // Europe/Madrid 2026-03-29: clocks jump 02:00 -> 03:00, so 02:30 never happens.
        // A lenient Calendar silently moves it to 03:30; the round-trip check must reject it.
        val default = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Madrid"))
            assertNull(epochSecondsFrom("2026-03-29", "02:30"))
            // Same day outside the gap is fine.
            assertNotNull(epochSecondsFrom("2026-03-29", "03:30"))
        } finally {
            java.util.TimeZone.setDefault(default)
        }
    }

    @Test
    fun awayEndTimeOrError_rejectsPastAndAcceptsFuture() {
        val now = 1_800_000_000L // fixed reference instant
        assertNull(awayEndTimeOrError("2020-01-01", "10:00", now))
        assertNull(awayEndTimeOrError("not-a-date", "10:00", now))
        assertNotNull(awayEndTimeOrError("2099-01-01", "10:00", now))
    }
}
