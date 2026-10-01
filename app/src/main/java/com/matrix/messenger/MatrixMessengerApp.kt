package com.matrix.messenger

import android.app.Application
import com.matrix.messenger.receiver.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MatrixMessengerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
    }
}
