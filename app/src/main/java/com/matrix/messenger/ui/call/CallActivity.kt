package com.matrix.messenger.ui.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.matrix.messenger.service.CallService
import com.matrix.messenger.ui.theme.MatrixMessengerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PEER_USER_ID = "extra_peer_user_id"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_PEER_AVATAR_URL = "extra_peer_avatar_url"
        const val EXTRA_IS_VIDEO = "extra_is_video"
        const val EXTRA_IS_INCOMING = "extra_is_incoming"

        fun newIntent(
            context: Context,
            callId: String,
            roomId: String,
            peerUserId: String,
            peerName: String,
            peerAvatarUrl: String?,
            isVideo: Boolean,
            isIncoming: Boolean
        ): Intent {
            return Intent(context, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_PEER_USER_ID, peerUserId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_PEER_AVATAR_URL, peerAvatarUrl)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Настройка окна для звонка поверх lock screen
        setupWindowForCall()

        // Читаем параметры из Intent
        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        val peerUserId = intent.getStringExtra(EXTRA_PEER_USER_ID) ?: ""
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Неизвестный"
        val peerAvatarUrl = intent.getStringExtra(EXTRA_PEER_AVATAR_URL)
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        val isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)

        enableEdgeToEdge()

        setContent {
            MatrixMessengerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Запрос разрешений для звонка
                    val permissionsState = rememberMultiplePermissionsState(
                        permissions = buildList {
                            add(android.Manifest.permission.RECORD_AUDIO)
                            add(android.Manifest.permission.CAMERA)
                        }
                    )

                    val allPermissionsGranted = permissionsState.permissions.all { 
                        it.status.isGranted 
                    }

                    // Запрашиваем разрешения при первом запуске
                    LaunchedEffect(Unit) {
                        if (!allPermissionsGranted) {
                            permissionsState.launchMultiplePermissionRequest()
                        }
                    }

                    if (allPermissionsGranted) {
                        CallScreen(
                            roomId = roomId,
                            peerUserId = peerUserId,
                            peerName = peerName,
                            peerAvatarUrl = peerAvatarUrl,
                            isVideo = isVideo,
                            isOutgoing = !isIncoming,
                            onCallEnded = {
                                finish()
                            }
                        )
                    } else {
                        // Экран запроса разрешений
                        PermissionRequestScreen(
                            onGrantPermissions = {
                                permissionsState.launchMultiplePermissionRequest()
                            },
                            onDeny = {
                                finish()
                            }
                        )
                    }
                }

                // Обработка нажатия "Назад" — завершаем звонок
                BackHandler {
                    CallService.endCall(this@CallActivity)
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Перезапускаем Activity с новыми параметрами
        recreate()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Возвращаем окно в нормальное состояние
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }

    /**
     * Настройка окна для отображения звонка поверх lock screen
     */
    private fun setupWindowForCall() {
        // Показывать поверх lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // Держать экран включённым во время звонка
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Полноэкранный режим
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}

/**
 * Экран запроса разрешений для звонка
 */
@androidx.compose.runtime.Composable
private fun PermissionRequestScreen(
    onGrantPermissions: () -> Unit,
    onDeny: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .androidx.compose.foundation.background(androidx.compose.ui.graphics.Color(0xFF1A1C2E))
            .androidx.compose.foundation.layout.padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        androidx.compose.material3.Icon(
            imageVector = androidx.compose.material.icons.Icons.Default.Mic,
            contentDescription = null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = androidx.compose.ui.Modifier.size(64.dp)
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = androidx.compose.ui.Modifier.height(24.dp)
        )

        androidx.compose.material3.Text(
            text = "Требуются разрешения",
            color = androidx.compose.ui.graphics.Color.White,
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = androidx.compose.ui.Modifier.height(12.dp)
        )

        androidx.compose.material3.Text(
            text = "Для совершения звонков необходим доступ к микрофону и камере",
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        androidx.compose.foundation.layout.Spacer(
            modifier = androidx.compose.ui.Modifier.height(32.dp)
        )

        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.OutlinedButton(
                onClick = onDeny,
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White
                )
            ) {
                androidx.compose.material3.Text("Отмена")
            }

            androidx.compose.material3.Button(
                onClick = onGrantPermissions,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF6366F1)
                )
            ) {
                androidx.compose.material3.Text("Разрешить")
            }
        }
    }
}