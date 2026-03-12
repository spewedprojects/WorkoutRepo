package com.gratus.workoutrepo.widgets

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.gratus.workoutrepo.R
import com.gratus.workoutrepo.data.RoutineRepository
import com.gratus.workoutrepo.model.Routine
import java.util.Calendar

class WorkoutsRemoteViewsFactory(private val context: Context, intent: Intent) : RemoteViewsService.RemoteViewsFactory {

    private sealed class ListItem {
        data class Header(val title: String) : ListItem()
        data class Workout(val text: String, val isMajor: Boolean) : ListItem()
    }

    private var listItems: List<ListItem> = ArrayList()
    private var dayIndex = 0

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val routine: Routine = RoutineRepository.getActiveRoutine(context)
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        dayIndex = when (dayOfWeek) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }

        val dayWorkout = routine.days[dayIndex]
        val newList = mutableListOf<ListItem>()

        // Major Section
        val majorRaw = dayWorkout.majorWorkouts
        if (!majorRaw.isNullOrBlank()) {
            newList.add(ListItem.Header("Major:"))
            majorRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                newList.add(ListItem.Workout(it, true))
            }
        }

        // Minor Section
        val minorRaw = dayWorkout.minorWorkouts
        if (!minorRaw.isNullOrBlank()) {
            newList.add(ListItem.Header("Minor:"))
            minorRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                newList.add(ListItem.Workout(it, false))
            }
        }

        listItems = newList
    }

    override fun onDestroy() {}

    override fun getCount(): Int = listItems.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = listItems[position]
        return when (item) {
            is ListItem.Header -> {
                val views = RemoteViews(context.packageName, R.layout.list_item_header)
                views.setTextViewText(R.id.headerText, item.title)
                views
            }
            is ListItem.Workout -> {
                val layoutRes = if (item.isMajor) R.layout.list_item_major else R.layout.list_item_minor
                val views = RemoteViews(context.packageName, layoutRes)
                
                val textId = if (item.isMajor) R.id.workoutsMajor else R.id.workoutsMinor
                val formattedText = com.gratus.workoutrepo.utils.TextFormatUtils.formatBulletsForWidget(item.text)
                views.setTextViewText(textId, formattedText)

                val fillInIntent = Intent()
                fillInIntent.putExtra("EXTRA_DAY_INDEX", dayIndex)
                views.setOnClickFillInIntent(textId, fillInIntent)
                views
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}

