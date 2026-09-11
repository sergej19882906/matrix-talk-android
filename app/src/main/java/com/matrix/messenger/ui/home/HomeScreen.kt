package com.matrix.messenger.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.matrix.messenger.data.model.ChatRoom
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRoomClick: (String) -> Unit,
    onLogout: () -> Unit,
    onCreateChat: () -> Unit,
    onBridges: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var profileDialogVisible by remember { mutableStateOf(false) }
    var displayName by remember(uiState.currentUser?.displayName) {
        mutableStateOf(uiState.currentUser?.displayName.orEmpty())
    }
    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::uploadAvatar)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is com.matrix.messenger.data.model.UiEvent.NavigateToChat -> {
                    onRoomClick(event.roomId)
                }
                is com.matrix.messenger.data.model.UiEvent.NavigateToLogin -> {
                    onLogout()
                }
                is com.matrix.messenger.data.model.UiEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чаты") },
                actions = {
                    IconButton(onClick = { profileDialogVisible = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Профиль")
                    }
                    IconButton(onClick = onCreateChat) {
                        Icon(Icons.Default.Add, contentDescription = "Создать чат")
                    }
                    IconButton(onClick = onBridges) {
                        Icon(Icons.Default.Link, contentDescription = "Мосты")
                    }
                    IconButton(onClick = viewModel::logout) {
                        Icon(Icons.Default.Logout, contentDescription = "Выйти")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Поиск
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                placeholder = { Text("Поиск чатов...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            // Список чатов
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.rooms.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Нет чатов",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                }
                else -> {
                    LazyColumn {
                        items(uiState.rooms, key = { it.roomId }) { room ->
                            RoomItem(
                                room = room,
                                onClick = { viewModel.onRoomClick(room.roomId) }
                            )
                        }
                    }
                }

            }
        }
    }

    if (profileDialogVisible) {
        AlertDialog(
            onDismissRequest = { profileDialogVisible = false },
            title = { Text("Профиль") },
            text = {
                Column {
                    Text(
                        text = uiState.currentUser?.userId.orEmpty(),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { avatarPicker.launch(arrayOf("image/*")) }
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Загрузить аватар")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setDisplayName(displayName)
                        profileDialogVisible = false
                    },
                    enabled = displayName.isNotBlank()
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { profileDialogVisible = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun RoomItem(
    room: ChatRoom,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Аватар
            AsyncImage(
                model = room.avatarUrl,
                contentDescription = room.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium),
                placeholder = painterResource(android.R.drawable.ic_dialog_info),
                error = painterResource(android.R.drawable.ic_dialog_info)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Информация о чате
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = room.name ?: "Без названия",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = formatTime(room.lastMessage?.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = room.lastMessage?.let { msg ->
                            "${msg.senderName?.substringBefore(":") ?: "Unknown"}: ${msg.body}"
                        } ?: "Нет сообщений",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (room.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = room.unreadCount.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(timestamp: Long?): String {
    if (timestamp == null) return ""
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
