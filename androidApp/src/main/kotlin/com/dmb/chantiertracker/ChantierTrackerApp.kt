package com.dmb.chantiertracker

import android.app.Application
import com.dmb.chantiertracker.di.initKoin

class ChantierTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(this)
    }
}
