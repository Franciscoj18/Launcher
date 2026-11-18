package com.calma.launcher

import android.app.Application
import com.calma.launcher.data.Apps
import com.calma.launcher.data.Prefs

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Apps.init(this)
    }
}
