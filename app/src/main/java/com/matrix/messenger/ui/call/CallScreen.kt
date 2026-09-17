package com.matrix.messenger.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.matrix.messenger.data.model.CallState

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

    LaunchedEffect(roomId, peerUserId) {
        if (isOutgoing && callState is CallState.Idle) {
            viewModel.startCall(roomId, peerUserId, peerName, isVideo)
        }
    }

    LaunchedEffect(callState) {
        if (callState is CallState.Ended) {
            kotlinx.coroutines.delay(500)
            onCallEnded()
        }
    }

    val backgroundGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFF1A1C2E), Color(0xFF0F1419))
    )

    Box(
        modifier = Modifier.fillMaxSize().background(backgroundGradient).systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = peerName,
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (callState is CallState.Connected) viewModel.formatDuration(callDuration) else "Вызов...",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f))
                ) {
                    Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Микрофон", tint = Color.White)
                }
                
                IconButton(
                    onClick = { viewModel.endCall() },
                    modifier = Modifier.size(64.dp).clip(CircleShape).background(Color(0xFFEF4444))
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Завершить", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}