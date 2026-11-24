package com.example.cardreader

import android.app.Application

class CardReaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RemoteControlHub.init(this)
    }
}
