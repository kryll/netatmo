package com.arsys.netatmo.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.arsys.netatmo.MainActivity
import com.arsys.netatmo.data.local.AppDatabase
import com.arsys.netatmo.data.repository.dataStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import org.json.JSONArray

class ThermostatWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ThermostatWidgetEntryPoint {
        fun appDatabase(): AppDatabase
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read selected home ID from DataStore
        val prefs = context.dataStore.data.first()
        val selectedHomeId = prefs[stringPreferencesKey("selected_home_id")]

        // Access AppDatabase via Hilt EntryPoint (widgets cannot use @Inject)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ThermostatWidgetEntryPoint::class.java
        )
        val home = selectedHomeId?.let { entryPoint.appDatabase().homeCacheDao().getHome(it) }

        // Parse rooms from roomsJson
        data class RoomData(
            val currentTemp: Double,
            val targetTemp: Double,
            val heatingActive: Boolean
        )

        val rooms = mutableListOf<RoomData>()
        if (home != null) {
            try {
                val array = JSONArray(home.roomsJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    rooms += RoomData(
                        currentTemp = obj.optDouble("currentTemp", 0.0),
                        targetTemp = obj.optDouble("targetTemp", 0.0),
                        heatingActive = obj.optBoolean("heatingActive", false)
                    )
                }
            } catch (_: Exception) { /* malformed JSON — leave rooms empty */ }
        }

        val homeName = home?.name ?: "Sin hogar"
        val avgTemp = if (rooms.isNotEmpty()) rooms.map { it.currentTemp }.average() else 0.0
        val setpoint = if (rooms.isNotEmpty()) rooms.map { it.targetTemp }.average() else 0.0
        val heatingActive = rooms.any { it.heatingActive }

        provideContent {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF0F2840)))
                    .clickable(actionStartActivity(launchIntent)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = GlanceModifier.padding(12.dp)
                ) {
                    Text(
                        text = "%.1f°C".format(avgTemp),
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "Objetivo: %.1f°C".format(setpoint),
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = homeName,
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.5f)),
                            fontSize = 12.sp
                        )
                    )
                    if (heatingActive) {
                        Text(
                            text = "Calentando",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFFFB923C)),
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
