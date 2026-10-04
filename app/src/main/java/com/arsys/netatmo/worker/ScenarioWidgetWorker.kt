package com.arsys.netatmo.worker

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arsys.netatmo.ui.widget.ScenarioWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScenarioWidgetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Update widget UI after execution
        ScenarioWidget().updateAll(applicationContext)
        return Result.success()
    }
}
