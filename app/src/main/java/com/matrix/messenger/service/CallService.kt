package com.matrix.messenger.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.matrix.messenger.R
import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.ui.call.CallActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class CallService : Service() {

    @Inject
    lateinit var callRepository: CallRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false

    companion object {
        const val CHANNEL_ID = "call_channel"
        const val NOTIFICATION_ID = 1001

        // Actions для управления звонком из уведомления
        const val ACTION_ANSWER = "action_answer"
        const val ACTION_REJECT = "action_reject"
        const val ACTION_END = "action_end"
        const val ACTION_MUTE = "action_mute"

        // Extra данные
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"

        fun startIncomingCall(
            context: Context,
            callId: String,
            roomId: String,
            peerName: String,
            isVideo: Boolean
        ) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_ANSWER
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_IS_INCOMING, true)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun startOutgoingCall(
            context: Context,
            callId: String,
            roomId: String,
            peerName: String,
            isVideo: Boolean
        ) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_ANSWER
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_IS_INCOMING, false)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun endCall(context: Context) {
            val intent = Intent(context, CallService::class.java).apply {
                action = ACTION_END
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER -> handleStartCall(intent)
            ACTION_REJECT -> handleRejectCall()
            ACTION_END -> handleEndCall()
            ACTION_MUTE -> handleMuteToggle()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }

    private fun handleStartCall(intent: Intent) {
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: return
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Неизвестный"
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)

        if (!isServiceStarted) {
            val notification = if (isIncoming) {
                createIncomingCallNotification(callId, roomId, peerName, isVideo)
            } else {
                createOutgoingCallNotification(peerName, isVideo)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isServiceStarted = true
        } else {
            // Обновляем уведомление при смене состояния
            updateNotification(peerName, isVideo, isIncoming = false)
        }

        // Следим за состоянием звонка
        serviceScope.launch {
            callRepository.callState.collectLatest { state ->
                when (state) {
                    is com.matrix.messenger.data.model.CallState.Connected -> {
                        updateNotification(peerName, isVideo, isIncoming = false)
                    }
                    is com.matrix.messenger.data.model.CallState.Ended,
                    com.matrix.messenger.data.model.CallState.Idle -> {
                        stopSelf()
                    }
                    else -> { /* ignore */ }
                }
            }
        }
    }

    private fun handleRejectCall() {
        serviceScope.launch {
            callRepository.rejectCall()
            stopSelf()
        }
    }

    private fun handleEndCall() {
        serviceScope.launch {
            callRepository.endCall()
            stopSelf()
        }
    }

    private fun handleMuteToggle() {
        serviceScope.launch {
            val currentState = callRepository.callState.value
            // Логика переключения микрофона
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Звонки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о входящих и активных звонках"
                setSound(null, null) // Звук управляется через звонок
                enableVibration(true)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createIncomingCallNotification(
        callId: String,
        roomId: String,
        peerName: String,
        isVideo: Boolean
    ): Notification {
        val fullScreenIntent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_ROOM_ID, roomId)
            putExtra(EXTRA_PEER_NAME, peerName)
            putExtra(EXTRA_IS_VIDEO, isVideo)
            putExtra(EXTRA_IS_INCOMING, true)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Кнопка "Принять"
        val answerIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_ANSWER
            putExtra(EXTRA_CALL_ID, callId)
            putExtra(EXTRA_IS_INCOMING, false) // После принятия - активный звонок
        }
        val answerPendingIntent = PendingIntent.getService(
            this, 1, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Кнопка "Отклонить"
        val rejectIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_REJECT
        }
        val rejectPendingIntent = PendingIntent.getService(
            this, 2, rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call) // Замените на вашу иконку
            .setContentTitle("Входящий звонок")
            .setContentText(peerName)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(R.drawable.ic_call, "Принять", answerPendingIntent)
            .addAction(R.drawable.ic_call_end, "Отклонить", rejectPendingIntent)
            .build()
    }

    private fun createOutgoingCallNotification(
        peerName: String,
        isVideo: Boolean
    ): Notification {
        val endIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_END
        }
        val endPendingIntent = PendingIntent.getService(
            this, 3, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(if (isVideo) "Видеозвонок" else "Аудиозвонок")
            .setContentText("Вызов: $peerName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_call_end, "Завершить", endPendingIntent)
            .build()
    }

    private fun updateNotification(
        peerName: String,
        isVideo: Boolean,
        isIncoming: Boolean
    ) {
        val endIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_END
        }
        val endPendingIntent = PendingIntent.getService(
            this, 3, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, CallService::class.java).apply {
            action = ACTION_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this, 4, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle("В разговоре")
            .setContentText(peerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(R.drawable.ic_mic, "Микрофон", mutePendingIntent)
            .addAction(R.drawable.ic_call_end, "Завершить", endPendingIntent)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MatrixTalk::CallWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(60 * 60 * 1000L) // Максимум 1 час
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}