package com.matrix.messenger.ui.chat

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.matrix.messenger.ui.call.CallActivity
import com.matrix.messenger.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    peerUserId: String,
    peerName: String,
    peerAvatarUrl: String?,
    navController: NavController,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState()
    val roomName by viewModel.roomName.collectAsState()
    val roomAvatarUrl by viewModel.roomAvatarUrl.collectAsState()
    
    var messageText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()
    
    // Автоматическая прокрутка вниз при новом сообщении
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        // Аватар собеседника
                        if (peerAvatarUrl != null || roomAvatarUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(peerAvatarUrl ?: roomAvatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = peerName,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        
                        Column {
                            Text(
                                text = roomName ?: peerName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (isTyping) {
                                Text(
                                    text = "печатает...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },
                actions = {
                    // 🆕 Кнопка аудио-звонка
                    IconButton(
                        onClick = {
                            val intent = CallActivity.newIntent(
                                context = context,
                                callId = "", // Будет сгенерирован в CallRepository
                                roomId = roomId,
                                peerUserId = peerUserId,
                                peerName = peerName,
                                peerAvatarUrl = peerAvatarUrl,
                                isVideo = false,
                                isIncoming = false
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Аудио звонок"
                        )
                    }
                    
                    // 🆕 Кнопка видео-звонка
                    IconButton(
                        onClick = {
                            val intent = CallActivity.newIntent(
                                context = context,
                                callId = "",
                                roomId = roomId,
                                peerUserId = peerUserId,
                                peerName = peerName,
                                peerAvatarUrl = peerAvatarUrl,
                                isVideo = true,
                                isIncoming = false
                            )
                            context.startActivity(intent)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Видео звонок"
                        )
                    }
                    
                    // Меню (опционально)
                    IconButton(onClick = { /* TODO: показать меню */ }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Меню"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Список сообщений
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message)
                }
            }
            
            // Поле ввода сообщения
            MessageInput(
                text = messageText,
                onTextChange = { 
                    messageText = it
                    // TODO: отправить typing indicator
                },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                onAttachFile = {
                    // TODO: открыть выбор файла
                }
            )
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val isOwnMessage = message.isOwn
    val alignment = if (isOwnMessage) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isOwnMessage) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isOwnMessage) 16.dp else 4.dp,
                bottomEnd = if (isOwnMessage) 4.dp else 16.dp
            ),
            color = backgroundColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = message.time,
                    color = textColor.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun MessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachFile: () -> Unit
) {
    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка прикрепления файла
            IconButton(onClick = onAttachFile) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Прикрепить файл"
                )
            }
            
            // Поле ввода
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Сообщение") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() }
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Кнопка отправки
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Отправить",
                    tint = if (text.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}

// Модель сообщения (если ещё нет)
data class Message(
    val id: String,
    val content: String,
    val time: String,
    val isOwn: Boolean,
    val senderId: String?
)