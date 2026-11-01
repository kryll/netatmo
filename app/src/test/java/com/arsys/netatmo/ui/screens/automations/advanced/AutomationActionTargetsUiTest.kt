package com.arsys.netatmo.ui.screens.automations.advanced

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/**
 * The three action targets José asked for (2026-11-01): pick a Netatmo program, an app
 * scenario ("escena") and another automation. Renders the real action editor sheet with
 * fake lists (no account, no network) and writes PNG captures of each picker into
 * app/build/outputs/compose-shots/ for visual inspection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomationActionTargetsUiTest {

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

    private val scenarios = listOf(
        ScenarioEntity(id = 1L, name = "Día", homeId = "home1", actionsJson = "[]"),
        ScenarioEntity(id = 2L, name = "Noche", homeId = "home1", actionsJson = "[]")
    )
    private val schedules = listOf(
        ScheduleOption(homeId = "home1", id = "sch-1", name = "Semana"),
        ScheduleOption(homeId = "home1", id = "sch-2", name = "Fin de semana")
    )
    private val automations = listOf(
        AutomationOption(id = 7L, name = "Convivencia fuera"),
        AutomationOption(id = 9L, name = "Noche fría")
    )

    private fun openSheet(onAdd: (Map<String, Any>) -> Unit, initial: Map<String, Any>? = null) {
        composeRule.setContent {
            NetatmoTheme {
                ActionEditorBottomSheet(
                    onDismiss = {},
                    onAdd = onAdd,
                    initial = initial,
                    scenarios = scenarios,
                    schedules = schedules,
                    automations = automations
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun actionEditor_newAction_offersProgramScenarioAndAutomation() {
        openSheet(onAdd = {})
        captureSheet("action-add-type-picker")

        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onFirst().assertIsDisplayed()
        composeRule.onNodeWithText("Programa", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Escenario", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Automatización", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun actionEditor_programAction_picksScheduleAndSaves() {
        var saved: Map<String, Any>? = null
        openSheet(onAdd = { saved = it })
        composeRule.onNodeWithText("Programa", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        captureSheet("action-add-programa")

        composeRule.onNodeWithText("Semana", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Fin de semana", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Fin de semana", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onLast().performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("set_schedule", saved?.get("type"))
        assertEquals("sch-2", saved?.get("scheduleId"))
        assertEquals("Fin de semana", saved?.get("scheduleName"))
        assertEquals("home1", saved?.get("homeId"))
    }

    @Test
    fun actionEditor_automationAction_picksAutomationAndSaves() {
        var saved: Map<String, Any>? = null
        openSheet(onAdd = { saved = it })
        composeRule.onNodeWithText("Automatización", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        captureSheet("action-add-automatizacion")

        composeRule.onNodeWithText("Convivencia fuera", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Noche fría", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onLast().performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("run_automation", saved?.get("type"))
        assertEquals(9L, saved?.get("automationId"))
        assertEquals("Noche fría", saved?.get("automationName"))
    }

    @Test
    fun actionEditor_scenarioAction_offeredForNewActionsAndSaves() {
        var saved: Map<String, Any>? = null
        openSheet(onAdd = { saved = it })
        composeRule.onNodeWithText("Escenario", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        captureSheet("action-add-escenario")

        composeRule.onNodeWithText("Noche", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onLast().performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("apply_scenario", saved?.get("type"))
        assertEquals(2L, saved?.get("scenarioId"))
        assertEquals("Noche", saved?.get("scenarioName"))
    }

    @Test
    fun actionEditor_editingAutomationAction_prefillsTarget() {
        var saved: Map<String, Any>? = null
        openSheet(
            onAdd = { saved = it },
            initial = mapOf("type" to "run_automation", "automationId" to 7L, "automationName" to "Convivencia fuera")
        )
        composeRule.waitForIdle()
        captureSheet("action-edit-automatizacion")

        composeRule.onNodeWithText("Editar acción", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Convivencia fuera", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Guardar cambios", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()

        assertEquals("run_automation", saved?.get("type"))
        assertEquals(7L, saved?.get("automationId"))
        assertEquals("Convivencia fuera", saved?.get("automationName"))
    }

    @Test
    fun actionEditor_automationAction_footerReachableByScroll() {
        // The sheet must scroll so the note and the save button are reachable at
        // narrow sizes; capture the scrolled footer for visual inspection.
        var saved: Map<String, Any>? = null
        openSheet(onAdd = { saved = it })
        composeRule.onNodeWithText("Automatización", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Noche fría", useUnmergedTree = true).performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onLast().performScrollTo()
        composeRule.waitForIdle()
        captureSheet("action-add-automatizacion-footer")

        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onLast().assertIsDisplayed()
        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onLast().assertIsEnabled()
        composeRule.onAllNodesWithText("Añadir acción", useUnmergedTree = true).onLast().performClick()
        composeRule.waitForIdle()
        assertEquals("run_automation", saved?.get("type"))
        assertEquals(9L, saved?.get("automationId"))
    }
}
