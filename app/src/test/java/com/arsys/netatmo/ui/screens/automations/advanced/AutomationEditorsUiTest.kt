package com.arsys.netatmo.ui.screens.automations.advanced

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.isToggleable
import com.arsys.netatmo.data.local.entities.ScenarioEntity
import com.arsys.netatmo.ui.theme.NetatmoTheme
import org.junit.Assert.assertEquals
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
import java.util.Calendar

/**
 * Renders the three automation editor sheets with fake data (no account, no network)
 * and checks that editing an existing item opens prefilled with "Guardar cambios".
 * Each test also writes a PNG capture of the sheet dialog into
 * app/build/outputs/compose-shots/ for visual inspection from CI artifacts.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: booting the real NetatmoApp would run Hilt injection, which
// opens EncryptedSharedPreferences (AndroidKeyStore) - not available under Robolectric
// and not needed by these sheets.
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomationEditorsUiTest {

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

    private fun localMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun triggerEditor_editingDatetimeTrigger_prefillsDateAndTime() {
        // The user's original case: an existing one-shot trigger with a wrong date must be fixable.
        val at = localMs(2026, 11, 15, 8, 30)
        var saved: Map<String, Any>? = null
        composeRule.setContent {
            NetatmoTheme {
                TriggerEditorBottomSheet(
                    onDismiss = {},
                    onAdd = { saved = it },
                    initial = mapOf("type" to "datetime", "platform" to "time", "at" to at)
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("trigger-edit-datetime")

        composeRule.onNodeWithText("Editar disparador", useUnmergedTree = true).assertIsDisplayed()
        // Normal phone viewport: these sit below the fold and must be reachable by
        // scrolling the sheet, exactly like a user would.
        composeRule.onNodeWithText("15/11/2026", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("08:30", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Guardar cambios", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Guardar cambios", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("datetime", saved?.get("type"))
        assertEquals(at, saved?.get("at"))
    }

    @Test
    fun triggerEditor_editingSunTrigger_showsSunChip() {
        composeRule.setContent {
            NetatmoTheme {
                TriggerEditorBottomSheet(
                    onDismiss = {},
                    onAdd = {},
                    initial = mapOf("type" to "sun", "event" to "sunrise", "offset" to -15)
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("trigger-edit-sun")

        composeRule.onNodeWithText("Editar disparador", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sol", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Salida del sol", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun conditionEditor_editingTimeWindow_prefillsAndSavesEditedValue() {
        var saved: Map<String, Any>? = null
        composeRule.setContent {
            NetatmoTheme {
                ConditionEditorBottomSheet(
                    onDismiss = {},
                    onAdd = { saved = it },
                    initial = mapOf("type" to "time_range", "after" to "22:30", "before" to "06:15")
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("condition-edit-timewindow")

        composeRule.onNodeWithText("Editar condición", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("22:30", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("06:15", useUnmergedTree = true).performScrollTo().assertIsDisplayed()

        // Edit the start hour, scroll down like a user, and save: the stored map
        // must carry the edited value, not the original one.
        composeRule.onNodeWithText("22:30", useUnmergedTree = true).performTextReplacement("23:15")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Guardar cambios", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("time_range", saved?.get("type"))
        assertEquals("23:15", saved?.get("after"))
        assertEquals("06:15", saved?.get("before"))
    }

    @Test
    fun actionEditor_editingApplyScenario_offersScenarioPickerAndSavesNewChoice() {
        val scenarios = listOf(
            ScenarioEntity(id = 1L, name = "Día", homeId = "home1", actionsJson = "[]"),
            ScenarioEntity(id = 2L, name = "Noche", homeId = "home1", actionsJson = "[]")
        )
        var saved: Map<String, Any>? = null
        composeRule.setContent {
            NetatmoTheme {
                ActionEditorBottomSheet(
                    onDismiss = {},
                    onAdd = { saved = it },
                    initial = mapOf("type" to "apply_scenario", "scenarioId" to 2L, "scenarioName" to "Noche"),
                    scenarios = scenarios
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("action-edit-scenario")

        composeRule.onNodeWithText("Editar acción", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Día", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Noche", useUnmergedTree = true).assertIsDisplayed()

        // Change the scenario and save: the stored map must point at the new scenario.
        composeRule.onNodeWithText("Día", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Guardar cambios", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("apply_scenario", saved?.get("type"))
        assertEquals(1L, saved?.get("scenarioId"))
        assertEquals("Día", saved?.get("scenarioName"))
    }

    @Test
    fun actionEditor_editingSetTemperature_keepsLegacyEndAtNote() {
        composeRule.setContent {
            NetatmoTheme {
                ActionEditorBottomSheet(
                    onDismiss = {},
                    onAdd = {},
                    initial = mapOf("type" to "set_temperature", "temperature" to 21.0, "endAt" to 1731800000000L)
                )
            }
        }
        composeRule.waitForIdle()
        captureSheet("action-edit-settemp")

        composeRule.onNodeWithText("Editar acción", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasText("21", substring = true), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun conditionEditor_calendarEventAndNegate_offersBothAndSavesFlags() {
        // The new pieces: a "Calendario vigente" condition type and the "Negar" switch.
        var saved: Map<String, Any>? = null
        composeRule.setContent {
            NetatmoTheme {
                ConditionEditorBottomSheet(
                    onDismiss = {},
                    onAdd = { saved = it }
                )
            }
        }
        composeRule.waitForIdle()

        // Without the calendar permission the picker explains itself instead of listing.
        composeRule.onNodeWithText("Calendario vigente", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Se necesita permiso de calendario para listar tus calendarios.", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("Conceder permiso", useUnmergedTree = true).assertIsDisplayed()

        // Negate a time-range condition: the saved map must carry the invert flag.
        composeRule.onNodeWithText("Rango horario", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Negar: se cumple cuando NO pasa lo de arriba", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        captureSheet("condition-add-calendar-event-negate")
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Añadir condición", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("time_range", saved?.get("type"))
        assertEquals(true, saved?.get("invert"))
    }

    @Test
    fun triggerEditor_allMode_showsNegateSwitchAndSavesInvert() {
        // In "Todas" mode each trigger can be negated; in "Cualquiera" the switch stays hidden
        // because a punctual occurrence cannot be negated.
        var saved: Map<String, Any>? = null
        composeRule.setContent {
            NetatmoTheme {
                TriggerEditorBottomSheet(
                    onDismiss = {},
                    onAdd = { saved = it },
                    triggersMode = "all"
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Negar: en modo «Todas» cuenta como cumplido cuando NO lo está", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        captureSheet("trigger-add-all-mode-negate")
        composeRule.onAllNodes(isToggleable(), useUnmergedTree = true)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Añadir disparador", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("time", saved?.get("type"))
        assertEquals(true, saved?.get("invert"))
    }

    @Test
    fun triggerEditor_anyMode_hidesNegateSwitch() {
        composeRule.setContent {
            NetatmoTheme {
                TriggerEditorBottomSheet(
                    onDismiss = {},
                    onAdd = {},
                    triggersMode = "any"
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Negar: en modo «Todas» cuenta como cumplido cuando NO lo está", useUnmergedTree = true)
            .assertDoesNotExist()
    }
}
