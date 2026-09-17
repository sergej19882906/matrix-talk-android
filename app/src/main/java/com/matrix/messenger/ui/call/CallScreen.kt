package com.matrix.messenger.ui.call

import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.matrix.messenger.data.model.CallState
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    roomId: String,
    peerUserId: String,
    peerName: String,
    peerAvatarUrl: String?,
    isVideo: Boolean,
    isOutgoing: Boolean,
    onCallEnded: () -> Unit,
    viewModel: CallViewModel = hiltViewModel()
) {
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isVideoEnabled by viewModel.isVideoEnabled.collectAsState()
    val callDuration by viewModel.callDuration.collectAsState()

    var isSpeakerOn by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }

    // Запуск звонка при первом входе
    LaunchedEffect(roomId, peerUserId) {
        if (isOutgoing && callState is CallState.Idle) {
            viewModel.startCall(roomId, peerUserId, peerName, isVideo)
        }
    }

    // Завершение экрана после окончания звонка
    LaunchedEffect(callState) {
        if (callState is CallState.Ended) {
            kotlinx.coroutines.delay(500)
            onCallEnded()
        }
    }

    // Градиентный фон
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
            .systemBarsPadding()
    ) {
        // Видео фон (если видеозвонок и включено видео)
        if (isVideo && callState is CallState.Connected && isVideoEnabled) {
            RemoteVideoView(
                modifier = Modifier.fillMaxSize()
            )
        }

        // Основной контент
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Верхняя панель с информацией
            CallHeader(
                peerName = peerName,
                peerAvatarUrl = peerAvatarUrl,
                callState = callState,
                callDuration = callDuration,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Локальное видео-превью (в углу)
            if (isVideo && isVideoEnabled && callState is CallState.Connected) {
                LocalVideoPreview(
                    isFrontCamera = isFrontCamera,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(120.dp, 160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                )
            }

            // Панель управления
            CallControls(
                callState = callState,
                isMuted = isMuted,
                isVideoEnabled = isVideoEnabled,
                isSpeakerOn = isSpeakerOn,
                isVideoCall = isVideo,
                onMuteToggle = { viewModel.toggleMute() },
                onVideoToggle = { viewModel.toggleVideo() },
                onSpeakerToggle = { isSpeakerOn = !isSpeakerOn },
                onCameraSwitch = {
                    isFrontCamera = !isFrontCamera
                    viewModel.switchCamera()
                },
                onEndCall = { viewModel.endCall() },
                onAcceptCall = {
                    viewModel.acceptCall(
                        callId = "", // Получаем из ViewModel
                        roomId = roomId,
                        peerName = peerName,
                        isVideo = isVideo
                    )
                },
                onRejectCall = { viewModel.rejectCall() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )
        }

        // Индикатор завершения звонка
        AnimatedVisibility(
            visible = callState is CallState.Ended,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Звонок завершён",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}

/**
 * Верхняя панель с информацией о звонке
 */
@Composable
private fun CallHeader(
    peerName: String,
    peerAvatarUrl: String?,
    callState: CallState,
    callDuration: Long,
    viewModel: CallViewModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Аватар с пульсацией при вызове
        Box(contentAlignment = Alignment.Center) {
            // Пульсирующие круги при вызове
            if (callState is CallState.Incoming || callState is CallState.Outgoing) {
                PulsingCircles()
            }

            // Аватар
            if (peerAvatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(peerAvatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = peerName,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Заглушка с инициалами
                Box(
                    modifier = Modifier
                        .size(120.dp)
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
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Имя собеседника
        Text(
            text = peerName,
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Статус звонка
        CallStatusText(callState = callState, callDuration = callDuration, viewModel = viewModel)
    }
}

/**
 * Текст статуса звонка
 */
@Composable
private fun CallStatusText(
    callState: CallState,
    callDuration: Long,
    viewModel: CallViewModel
) {
    val (text, color) = when (callState) {
        is CallState.Idle -> "Подготовка..." to Color.White.copy(alpha = 0.7f)
        is CallState.Incoming -> "Входящий звонок" to Color(0xFF4ADE80)
        is CallState.Outgoing -> "Вызов..." to Color.White.copy(alpha = 0.7f)
        is CallState.Connected -> viewModel.formatDuration(callDuration) to Color(0xFF4ADE80)
        is CallState.Ended -> "Звонок завершён" to Color(0xFFEF4444)
    }

    AnimatedContent(
        targetState = text,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "call_status"
    ) { statusText ->
        Text(
            text = statusText,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Пульсирующие круги вокруг аватара при вызове
 */
@Composable
private fun PulsingCircles() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale1"
    )
    val alpha1 by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha1"
    )

    val scale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale2"
    )
    val alpha2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha2"
    )

    // Первый круг
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale1)
            .clip(CircleShape)
            .background(Color(0xFF6366F1).copy(alpha = alpha1))
    )

    // Второй круг
    Box(
        modifier = Modifier
            .size(140.dp)
            .scale(scale2)
            .clip(CircleShape)
            .background(Color(0xFF8B5CF6).copy(alpha = alpha2))
    )
}

/**
 * Панель управления звонком
 */
@Composable
private fun CallControls(
    callState: CallState,
    isMuted: Boolean,
    isVideoEnabled: Boolean,
    isSpeakerOn: Boolean,
    isVideoCall: Boolean,
    onMuteToggle: () -> Unit,
    onVideoToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onCameraSwitch: () -> Unit,
    onEndCall: () -> Unit,
    onAcceptCall: () -> Unit,
    onRejectCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (callState) {
        is CallState.Incoming -> {
            // Кнопки "Принять" и "Отклонить" для входящего звонка
            Row(
                modifier = modifier,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "Отклонить",
                    backgroundColor = Color(0xFFEF4444),
                    onClick = onRejectCall,
                    size = 72.dp
                )

                Spacer(modifier = Modifier.width(48.dp))

                CallControlButton(
                    icon = Icons.Default.Call,
                    label = "Принять",
                    backgroundColor = Color(0xFF22C55E),
                    onClick = onAcceptCall,
                    size = 72.dp
                )
            }
        }

        is CallState.Outgoing, is CallState.Connected, is CallState.Ended -> {
            // Полный набор элементов управления
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Верхний ряд: микрофон, видео, динамик, камера
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CallControlButton(
                        icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = if (isMuted) "Включить" else "Выключить",
                        backgroundColor = if (isMuted) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        onClick = onMuteToggle,
                        enabled = callState is CallState.Connected
                    )

                    if (isVideoCall) {
                        CallControlButton(
                            icon = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                            label = if (isVideoEnabled) "Камера вкл" else "Камера выкл",
                            backgroundColor = if (!isVideoEnabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                            onClick = onVideoToggle,
                            enabled = callState is CallState.Connected
                        )
                    }

                    CallControlButton(
                        icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        label = if (isSpeakerOn) "Динамик" else "Тихо",
                        backgroundColor = if (isSpeakerOn) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.1f),
                        onClick = onSpeakerToggle,
                        enabled = callState is CallState.Connected
                    )

                    if (isVideoCall && isVideoEnabled) {
                        CallControlButton(
                            icon = Icons.Default.Cameraswitch,
                            label = "Камера",
                            backgroundColor = Color.White.copy(alpha = 0.1f),
                            onClick = onCameraSwitch,
                            enabled = callState is CallState.Connected
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Кнопка завершения звонка
                CallControlButton(
                    icon = Icons.Default.CallEnd,
                    label = "Завершить",
                    backgroundColor = Color(0xFFEF4444),
                    onClick = onEndCall,
                    size = 72.dp
                )
            }
        }

        else -> { /* Idle */ }
    }
}

/**
 * Кнопка управления звонком
 */
@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    backgroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 56.dp
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    if (enabled) backgroundColor
                    else backgroundColor.copy(alpha = 0.3f)
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(size / 2)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            color = Color.White.copy(alpha = if (enabled) 0.9f else 0.4f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Локальное видео-превью (в углу экрана)
 */
@Composable
private fun LocalVideoPreview(
    isFrontCamera: Boolean,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                // Инициализация WebRTC рендерера
                // В реальном приложении нужно получить локальный видеотрек из CallRepository
            }
        },
        modifier = modifier
            .background(Color.Black)
    )
}

/**
 * Удалённое видео (фон экрана)
 */
@Composable
private fun RemoteVideoView(
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).apply {
                // Инициализация WebRTC рендерера для удалённого видео
                // В реальном приложении нужно получить удалённый видеотрек из CallRepository
            }
        },
        modifier = modifier.background(Color.Black)
    )
}