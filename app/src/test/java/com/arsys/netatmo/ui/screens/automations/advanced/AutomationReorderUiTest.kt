package com.arsys.netatmo.ui.screens.automations.advanced

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.arsys.netatmo.ui.theme.NetatmoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * José's case: he added the notification before the away-mode action by mistake and
 * wants to swap their order. Renders real action pills with the reorder arrows,
 * captures PNGs before/after the move into app/build/outputs/compose-shots/.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AutomationReorderUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun captureActivity(name: String) {
        val view = composeRule.activity.window.decorView
        assertTrue("activity has no width for $name", view.width > 0)
        assertTrue("activity has no height for $name", view.height > 0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/outputs/compose-shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun actionPills_reorderArrows_swapNotificationAndAwayMode() {
        val notify = AutomationAction(type = "notify", title = "Aviso convivencia")
        val away = AutomationAction(type = "set_home_mode", mode = "away", durationHours = 3.5)
        val temp = AutomationAction(type = "set_temperature", temperature = 19.5)
        val actions = mutableStateOf(listOf(notify, away, temp))

        composeRule.setContent {
            NetatmoTheme {
                Column {
                    actions.value.forEachIndexed { index, action ->
                        ActionPill(
                            action = action,
                            onEdit = {},
                            onDelete = {},
                            onMoveUp = if (index > 0) ({ actions.value = actions.value.movedWithin(index, -1) }) else null,
                            onMoveDown = if (index < actions.value.lastIndex) ({ actions.value = actions.value.movedWithin(index, 1) }) else null
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Three actions: the middle one has both arrows, edges have one each.
        composeRule.onAllNodesWithContentDescription("Subir").assertCountEquals(2)
        composeRule.onAllNodesWithContentDescription("Bajar").assertCountEquals(2)
        captureActivity("reorder-actions-before")

        // Initial vertical order: notification above away mode.
        fun topOf(text: String) =
            composeRule.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot.top
        val notifyBefore = topOf("Notificación: Aviso convivencia")
        val awayBefore = topOf("Modo de la casa: Ausente durante 3,5 h")
        assertTrue("notification should start above away mode", notifyBefore < awayBefore)

        // José's fix: move the notification below the away mode.
        composeRule.onAllNodesWithContentDescription("Bajar")[0].performClick()
        composeRule.waitForIdle()
        captureActivity("reorder-actions-after")

        val notifyAfter = topOf("Notificación: Aviso convivencia")
        val awayAfter = topOf("Modo de la casa: Ausente durante 3,5 h")
        assertTrue("notification should end below away mode", notifyAfter > awayAfter)
        composeRule.onNodeWithText("Fijar temperatura a 19.5°C").assertIsDisplayed()
    }

    @Test
    fun triggerPill_singleItem_hasNoArrows() {
        composeRule.setContent {
            NetatmoTheme {
                TriggerPill(
                    trigger = AutomationTrigger(type = "time", hour = 9, minute = 30, days = listOf(6, 7)),
                    onEdit = {},
                    onDelete = {},
                    onMoveUp = null,
                    onMoveDown = null
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("Subir").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Bajar").assertCountEquals(0)
        composeRule.onNodeWithText("Hora: 09:30 · S, D").assertIsDisplayed()
    }
}
