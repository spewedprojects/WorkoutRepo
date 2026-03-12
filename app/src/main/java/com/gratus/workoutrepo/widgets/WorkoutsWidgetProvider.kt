package com.gratus.workoutrepo.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.gratus.workoutrepo.MainActivity
import com.gratus.workoutrepo.R
import com.gratus.workoutrepo.data.RoutineRepository
import com.gratus.workoutrepo.model.Routine
import java.util.Calendar
import androidx.core.net.toUri

class WorkoutsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_card_week)

        val routine: Routine = RoutineRepository.getActiveRoutine(context)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val dayIndex = when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        val dayNames = arrayOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")
        val dayWorkout = routine.days[dayIndex]

        views.setTextViewText(R.id.weekDay, dayNames[dayIndex])
        views.setTextViewText(R.id.workoutType, dayWorkout.workoutType ?: "(No workout)")

        // Setup Single Unified ListView
        setupListView(context, views, R.id.workouts, appWidgetId)

        // Template intent for list item clicks
        val clickIntent = Intent(context, MainActivity::class.java)
        val clickPendingIntent = PendingIntent.getActivity(
            context, 0, clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(R.id.workouts, clickPendingIntent)

        // Click on the widget title/background to open app
        val titleIntent = Intent(context, MainActivity::class.java)
        titleIntent.putExtra("EXTRA_DAY_INDEX", dayIndex)
        val titlePendingIntent = PendingIntent.getActivity(
            context, appWidgetId, titleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.cardViewWeek, titlePendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun setupListView(context: Context, views: RemoteViews, listViewId: Int, appWidgetId: Int) {
        val intent = Intent(context, WorkoutsRemoteViewsService::class.java)
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        // Data needs to be unique to force update
        intent.data = intent.toUri(Intent.URI_INTENT_SCHEME).toUri()
        views.setRemoteAdapter(listViewId, intent)
        views.setEmptyView(listViewId, android.R.id.empty)
    }

    companion object {
        fun sendRefreshBroadcast(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, WorkoutsWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            
            // Refresh data in factories
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.workouts)
            
            // Also trigger onUpdate to refresh basic views (day name, workout type)
            val intent = Intent(context, WorkoutsWidgetProvider::class.java)
            intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            context.sendBroadcast(intent)
        }
    }
}

