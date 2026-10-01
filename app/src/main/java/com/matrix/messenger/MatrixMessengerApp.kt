package com.matrix.messenger

import android.app.Application
import android.os.Build
import com.matrix.messenger.receiver.NotificationChannels
import com.matrix.messenger.receiver.OemBatteryHelper
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MatrixMessengerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
    }
}
