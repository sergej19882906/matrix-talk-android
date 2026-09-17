package com.matrix.messenger.ui.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.matrix.messenger.service.CallService
import com.matrix.messenger.ui.theme.MatrixMessengerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class IncomingCallActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_ROOM_ID = "extra_room_id"
        const val EXTRA_PEER_USER_ID = "extra_peer_user_id"
        const val EXTRA_PEER_NAME = "extra_peer_name"
        const val EXTRA_PEER_AVATAR_URL = "extra_peer_avatar_url"
        const val EXTRA_IS_VIDEO = "extra_is_video"

        fun newIntent(
            context: Context,
            callId: String,
            roomId: String,
            peerUserId: String,
            peerName: String,
            peerAvatarUrl: String?,
            isVideo: Boolean
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_PEER_USER_ID, peerUserId)
                putExtra(EXTRA_PEER_NAME, peerName)
                putExtra(EXTRA_PEER_AVATAR_URL, peerAvatarUrl)
                putExtra(EXTRA_IS_VIDEO, isVideo)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Настройка окна для полноэкранного отображения входящего звонка
        setupWindowForIncomingCall()

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val roomId = intent.getStringExtra(EXTRA_ROOM_ID) ?: ""
        val peerUserId = intent.getStringExtra(EXTRA_PEER_USER_ID) ?: ""
        val peerName = intent.getStringExtra(EXTRA_PEER_NAME) ?: "Неизвестный"
        val peerAvatarUrl = intent.getStringExtra(EXTRA_PEER_AVATAR_URL)
        val isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)

        enableEdgeToEdge()

        setContent {
            MatrixMessengerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IncomingCallScreen(
                        peerName = peerName,
                        peerAvatarUrl = peerAvatarUrl,
                        isVideo = isVideo,
                        onAccept = {
                            // Запускаем CallActivity для активного звонка
                            val callIntent = CallActivity.newIntent(
                                context = this@IncomingCallActivity,
                                callId = callId,
                                roomId = roomId,
                                peerUserId = peerUserId,
                                peerName = peerName,
                                peerAvatarUrl = peerAvatarUrl,
                                isVideo = isVideo,
                                isIncoming = true
                            )
                            startActivity(callIntent)
                            finish()
                        },
                        onReject = {
                            CallService.endCall(this@IncomingCallActivity)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun setupWindowForIncomingCall() {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}

/**
 * Экран входящего звонка с кнопками "Принять" и "Отклонить"
 */
@Composable
private fun IncomingCallScreen(
    peerName: String,
    peerAvatarUrl: String?,
    isVideo: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF1A1C2E),
            Color(0xFF0F1419)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Текст "Входящий звонок"
            Text(
                text = if (isVideo) "Входящий видеозвонок" else "Входящий аудиозвонок",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Аватар собеседника
            if (peerAvatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(peerAvatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = peerName,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF6366F1),
                                    Color(0xFF8B5CF6)
                                )
                            )
                        )
                        .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = peerName.take(2).uppercase(),
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Имя собеседника
            Text(
                text = peerName,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(64.dp))

            // Кнопки "Отклонить" и "Принять"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Кнопка "Отклонить"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onReject,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Отклонить",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Отклонить",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Кнопка "Принять"
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = onAccept,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF22C55E))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Принять",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Принять",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}