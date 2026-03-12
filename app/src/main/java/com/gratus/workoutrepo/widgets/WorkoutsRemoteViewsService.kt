package com.gratus.workoutrepo.widgets

import android.content.Intent
import android.widget.RemoteViewsService

class WorkoutsRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WorkoutsRemoteViewsFactory(this.applicationContext, intent)
    }
}
