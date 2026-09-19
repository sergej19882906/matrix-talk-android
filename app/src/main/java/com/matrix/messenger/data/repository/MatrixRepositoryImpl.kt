package com.matrix.messenger.data.repository

import android.net.Uri
import android.content.Context
import android.database.Cursor
import android.provider.OpenableColumns
import android.util.Log
import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.LastMessage
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.Matrix
import org.matrix.android.sdk.api.auth.LoginType
import org.matrix.android.sdk.api.auth.data.Credentials
import org.matrix.android.sdk.api.auth.data.HomeServerConnectionConfig
import org.matrix.android.sdk.api.auth.data.SessionParams
import org.matrix.android.sdk.api.failure.Failure
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.content.ContentAttachmentData
import org.matrix.android.sdk.api.session.events.model.isAudioMessage
import org.matrix.android.sdk.api.session.events.model.isFileMessage
import org.matrix.android.sdk.api.session.events.model.isImageMessage
import org.matrix.android.sdk.api.session.events.model.isTextMessage
import org.matrix.android.sdk.api.session.events.model.isVideoMessage
import org.matrix.android.sdk.api.session.room.RoomSortOrder
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.roomSummaryQueryParams
import org.matrix.android.sdk.api.session.room.timeline.Timeline
import org.matrix.android.sdk.api.session.room.timeline.TimelineEvent
import org.matrix.android.sdk.api.session.room.timeline.TimelineSettings
import org.matrix.android.sdk.api.session.room.timeline.getTextEditableContent
import javax.inject.Inject

/**
 * Реализация репозитория для работы с Matrix SDK
 */
class MatrixRepositoryImpl @Inject constructor(
    private val context: Context,
    private val matrix: Matrix
) : MatrixRepository {

    companion object {
        private const val TAG = "MatrixRepository"
    }

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var session: Session? = null
    private var timelineCache: MutableMap<String, Timeline> = mutableMapOf()

    private val _currentUser = MutableStateFlow<MatrixUser?>(null)
    override val currentUser: Flow<MatrixUser?> = _currentUser

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected("Not connected"))
    override val connectionState: Flow<ConnectionState> = _connectionState

    override suspend fun initialize() {
        try {
            Log.d(TAG, "Initializing Matrix SDK")
            _connectionState.value = ConnectionState.Connecting
            
            // Получаем последнюю активную сессию
            session = matrix.authenticationService().getLastAuthenticatedSession()
            
            if (session != null) {
                session?.open()
                setupSessionCallbacks()
                updateCurrentUser()
                _connectionState.value = ConnectionState.Connected
                Log.d(TAG, "Session restored successfully")
            } else {
                _connectionState.value = ConnectionState.Disconnected("No active session")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Matrix SDK", e)
            _connectionState.value = ConnectionState.Disconnected(e.message ?: "Unknown error")
        }
    }

    override suspend fun login(
        homeServer: String,
        username: String,
        password: String
    ): LoginResult {
        return try {
            Log.d(TAG, "Logging in to $homeServer as $username")
            _connectionState.value = ConnectionState.Connecting

            val homeServerUrl = normalizeHomeServer(homeServer)

            val config = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(homeServerUrl)
                .build()

            // Прямая аутентификация
            val newSession = matrix.authenticationService().directAuthentication(
                homeServerConnectionConfig = config,
                matrixId = username,
                password = password,
                initialDeviceName = "Matrix Talk Android"
            )
            
            session = newSession
            newSession.open()
            setupSessionCallbacks()
            updateCurrentUser()
            _connectionState.value = ConnectionState.Connected
            Log.d(TAG, "Login successful")
            LoginResult.Success(_currentUser.value?.userId ?: username)
        } catch (e: Failure) {
            Log.e(TAG, "Login failed", e)
            val errorMessage = failureMessage(e)
            _connectionState.value = ConnectionState.Disconnected(errorMessage)
            LoginResult.Error(errorMessage)
        } catch (e: Exception) {
            Log.e(TAG, "Login failed", e)
            val errorMessage = e.message ?: "Unknown error"
            _connectionState.value = ConnectionState.Disconnected(errorMessage)
            LoginResult.Error(errorMessage)
        }
    }

    override suspend fun loginWithToken(
        homeServer: String,
        userId: String,
        accessToken: String
    ): LoginResult {
        return try {
            require(userId.isNotBlank()) { "Matrix user id is required" }
            require(accessToken.isNotBlank()) { "Matrix access token is required" }
            _connectionState.value = ConnectionState.Connecting

            val homeServerUrl = normalizeHomeServer(homeServer)
            val config = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(homeServerUrl)
                .build()
            /*
             * SDK 1.6.50 has no public loginWithAccessToken helper.  Its
             * SessionManager is the supported low-level session factory used
             * after SSO/QR login, and accepts the same Matrix credentials.
             */
            val credentials = Credentials(
                userId,
                accessToken,
                null,
                homeServerUrl,
                "",
                null
            )
            val params = SessionParams(credentials, config, true, LoginType.CUSTOM)
            val sessionManager = matrix.javaClass
                .getMethod("getSessionManager\$matrix_sdk_android_release")
                .invoke(matrix)
            val newSession = sessionManager.javaClass
                .getMethod("getOrCreateSession", SessionParams::class.java)
                .invoke(sessionManager, params) as Session
            session = newSession
            newSession.open()
            setupSessionCallbacks()
            updateCurrentUser()
            _connectionState.value = ConnectionState.Connected
            LoginResult.Success(_currentUser.value?.userId ?: userId)
        } catch (e: Failure) {
            val message = failureMessage(e)
            _connectionState.value = ConnectionState.Disconnected(message)
            LoginResult.Error(message)
        } catch (e: Exception) {
            val message = e.message ?: "Token login failed"
            _connectionState.value = ConnectionState.Disconnected(message)
            LoginResult.Error(message)
        }
    }

    override suspend fun logout() {
        try {
            Log.d(TAG, "Logging out")
            session?.signOutService()?.signOut(true, false)
            session = null
            timelineCache.clear()
            _currentUser.value = null
            _connectionState.value = ConnectionState.Disconnected("Logged out")
        } catch (e: Exception) {
            Log.e(TAG, "Logout failed", e)
        }
    }

    override fun getRoomsFlow(): Flow<List<ChatRoom>> = callbackFlow {
        val currentSession = session
        if (currentSession == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val roomSummariesFlow = currentSession.roomService()
            .getRoomSummariesLive(
                roomSummaryQueryParams {},
                RoomSortOrder.PRIORITY_AND_ACTIVITY
            )
            .asFlow()

        val job = repositoryScope.launch {
            roomSummariesFlow.collect { summaries ->
                val rooms = summaries.map { summary -> summary.toChatRoom() }
                trySend(rooms)
            }
        }

        awaitClose {
            job.cancel()
        }
    }.distinctUntilChanged()

    // Вспомогательное расширение для LiveData
    private fun <T> androidx.lifecycle.LiveData<T>.asFlow(): Flow<T> = callbackFlow {
        val observer = androidx.lifecycle.Observer<T> { value -> trySend(value) }
        observeForever(observer)
        awaitClose { removeObserver(observer) }
    }

    override fun getMessagesFlow(roomId: String): Flow<List<Message>> = callbackFlow {
        val currentSession = session
        if (currentSession == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val room = currentSession.roomService().getRoom(roomId)
        if (room == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val timelineSettings = TimelineSettings(
            initialSize = 50
        )

        val timeline = room.timelineService().createTimeline(null, timelineSettings)
        timelineCache[roomId] = timeline
        timeline.start(null)

        val listener = object : Timeline.Listener {
            override fun onTimelineUpdated(snapshot: List<TimelineEvent>) {
                val events = timeline.getSnapshot()
                val messages = events.mapNotNull { event -> event.toMessage(currentSession.myUserId) }
                trySend(messages)
            }

            override fun onTimelineFailure(throwable: Throwable) {
                Log.e(TAG, "Timeline failure", throwable)
            }
        }

        timeline.addListener(listener)

        // Начальная загрузка
        val events: List<TimelineEvent> = timeline.getSnapshot()
        val messages = events.mapNotNull { event -> event.toMessage(currentSession.myUserId) }
        trySend(messages)

        awaitClose {
            timeline.removeListener(listener)
            timeline.dispose()
        }
    }.distinctUntilChanged()

    override suspend fun sendTextMessage(roomId: String, text: String) {
        try {
            Log.d(TAG, "Sending text message to $roomId")
            val room = session?.roomService()?.getRoom(roomId)
                ?: throw IllegalStateException("Room is not available: $roomId")
            room.sendService().sendTextMessage(text, "", false, emptyMap())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text message", e)
            throw e
        }
    }

    override suspend fun sendFileMessage(
        roomId: String,
        filePath: String,
        mimeType: String,
        caption: String?
    ) {
        try {
            Log.d(TAG, "Sending file message to $roomId")
            val currentSession = session ?: throw IllegalStateException("No active session")
            val room = currentSession.roomService().getRoom(roomId)
                ?: throw IllegalStateException("Room is not available: $roomId")
            val (uri, name, size, modified) = resolveFile(filePath)
            val safeMimeType = mimeType.trim().ifBlank {
                context.contentResolver.getType(uri) ?: "application/octet-stream"
            }
            val attachmentType = when {
                safeMimeType.startsWith("image/") -> ContentAttachmentData.Type.IMAGE
                safeMimeType.startsWith("video/") -> ContentAttachmentData.Type.VIDEO
                safeMimeType.startsWith("audio/") -> ContentAttachmentData.Type.AUDIO
                else -> ContentAttachmentData.Type.FILE
            }
            val attachment = ContentAttachmentData(
                size = size,
                duration = null,
                date = modified,
                height = null,
                width = null,
                exifOrientation = 0,
                name = name,
                queryUri = uri,
                mimeType = safeMimeType,
                type = attachmentType,
                waveform = emptyList()
            )
            val additionalContent = caption?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { mapOf<String, Any>("body" to it) }
                ?: emptyMap()
            room.sendService().sendMedia(
                attachment,
                false,
                emptySet(),
                null,
                null,
                additionalContent
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send file message", e)
            throw e
        }
    }

    override suspend fun getRoomInfo(roomId: String): ChatRoom? {
        return try {
            session?.roomService()?.getRoomSummary(roomId)?.toChatRoom()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get room info", e)
            null
        }
    }

    override suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean,
        userIds: List<String>
    ): String {
        return try {
            Log.d(TAG, "Creating room: $name")
            val params = org.matrix.android.sdk.api.session.room.model.create.CreateRoomParams().apply {
                this.name = name
                this.topic = topic
                this.invitedUserIds = userIds.toMutableList()
                this.isDirect = isDirect
            }
            
            session?.roomService()?.createRoom(params) ?: throw IllegalStateException("Failed to create room")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create room", e)
            throw e
        }
    }

    override suspend fun joinRoom(aliasOrId: String): String {
        return try {
            Log.d(TAG, "Joining room: $aliasOrId")
            val currentSession = session ?: throw IllegalStateException("No active session")
            currentSession.roomService().joinRoom(aliasOrId, null, emptyList())
            val roomId = currentSession.roomService()
                .getRoomIdByAlias(aliasOrId, true)
                .orNull()
                ?.roomId
            roomId ?: aliasOrId.takeIf { it.startsWith("!") }
                ?: throw IllegalStateException("Joined room id is unavailable")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join room", e)
            throw e
        }
    }

    override suspend fun leaveRoom(roomId: String) {
        try {
            Log.d(TAG, "Leaving room: $roomId")
            val currentSession = session ?: throw IllegalStateException("No active session")
            currentSession.roomService().leaveRoom(roomId, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave room", e)
            throw e
        }
    }

    override suspend fun markRoomAsRead(roomId: String) {
        try {
            val currentSession = session ?: throw IllegalStateException("No active session")
            currentSession.roomService().markAllAsRead(listOf(roomId))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark room as read", e)
        }
    }

    override suspend fun sendTypingNotification(roomId: String, isTyping: Boolean) {
        try {
            val room = session?.roomService()?.getRoom(roomId)
                ?: throw IllegalStateException("Room is not available: $roomId")
            if (isTyping) {
                room.typingService().userIsTyping()
            } else {
                room.typingService().userStopsTyping()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send typing notification", e)
        }
    }

    override suspend fun sendReaction(eventId: String, reaction: String) {
        require(reaction.isNotBlank()) { "Reaction must not be blank" }
        val currentSession = session ?: throw IllegalStateException("No active session")
        val room = findRoomForEvent(eventId)
            ?: throw IllegalStateException("Room for event is unavailable: $eventId")
        room.relationService().sendReaction(eventId, reaction)
    }

    override suspend fun editMessage(eventId: String, roomId: String, newText: String) {
        require(newText.isNotBlank()) { "Edited message must not be blank" }
        val currentSession = session ?: throw IllegalStateException("No active session")
        val room = currentSession.roomService().getRoom(roomId)
            ?: throw IllegalStateException("Room is not available: $roomId")
        val event = room.timelineService().getTimelineEvent(eventId)
            ?: throw IllegalStateException("Message is unavailable: $eventId")
        room.relationService().editTextMessage(
            event,
            "m.text",
            newText,
            newText,
            false,
            newText
        )
    }

    override suspend fun deleteMessage(eventId: String, roomId: String) {
        val currentSession = session ?: throw IllegalStateException("No active session")
        val room = currentSession.roomService().getRoom(roomId)
            ?: throw IllegalStateException("Room is not available: $roomId")
        val event = room.timelineService().getTimelineEvent(eventId)
            ?: throw IllegalStateException("Message is unavailable: $eventId")
        room.sendService().redactEvent(event.root, "Deleted by user", emptyList(), emptyMap())
    }

    override suspend fun uploadAvatar(filePath: String) {
        val currentSession = session ?: throw IllegalStateException("No active session")
        val (uri, _, _, _) = resolveFile(filePath)
        val mimeType = context.contentResolver.getType(uri)
            ?: "application/octet-stream"
        require(mimeType.startsWith("image/")) {
            "Avatar must be an image (received $mimeType)"
        }
        currentSession.profileService().updateAvatar(
            currentSession.myUserId,
            uri,
            mimeType
        )
        updateCurrentUser()
    }

    override suspend fun setDisplayName(name: String) {
        val currentSession = session ?: throw IllegalStateException("No active session")
        require(name.isNotBlank()) { "Display name must not be blank" }
        currentSession.profileService().setDisplayName(currentSession.myUserId, name.trim())
        updateCurrentUser()
    }

    private fun setupSessionCallbacks() {
        // Session callbacks are not required for the flows used by this app.
    }

    private fun failureMessage(failure: Failure): String {
        return when (failure) {
            is Failure.ServerError -> failure.error.message ?: "Matrix error"
            else -> failure.message ?: "Matrix error"
        }
    }

    private fun updateCurrentUser() {
        session?.let { s ->
            _currentUser.value = MatrixUser(
                userId = s.myUserId,
                displayName = s.userService().getUser(s.myUserId)?.displayName,
                avatarUrl = s.userService().getUser(s.myUserId)?.avatarUrl
            )
        }
    }

    private fun normalizeHomeServer(homeServer: String): String {
            val value = homeServer.trim()
            require(value.isNotBlank()) { "Home server is required" }
            return if (value.startsWith("http://") || value.startsWith("https://")) {
                value
            } else {
                "https://$value"
            }
        }

    private data class ResolvedFile(
            val uri: Uri,
            val name: String,
            val size: Long,
            val modified: Long
        )

    private fun resolveFile(filePathOrUri: String): ResolvedFile {
            require(filePathOrUri.isNotBlank()) { "File URI/path is required" }
            val parsedUri = Uri.parse(filePathOrUri)
            val uri = if (parsedUri.scheme.isNullOrBlank()) {
                val file = java.io.File(filePathOrUri)
                require(file.exists() && file.isFile && file.canRead()) {
                    "File is not readable: $filePathOrUri"
                }
                Uri.fromFile(file)
            } else {
                parsedUri
            }
            var name = uri.lastPathSegment?.substringAfterLast('/').orEmpty()
            var size = -1L
            if (uri.scheme == "content") {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        name = cursor.getStringOrNull(OpenableColumns.DISPLAY_NAME).orEmpty()
                        size = cursor.getLongOrNull(OpenableColumns.SIZE) ?: -1L
                    }
                }
            } else {
                val file = java.io.File(uri.path ?: filePathOrUri)
                require(file.exists() && file.isFile && file.canRead()) {
                    "File is not readable: $filePathOrUri"
                }
                name = file.name
                size = file.length()
                return ResolvedFile(uri, name, size, file.lastModified())
            }
            require(name.isNotBlank()) { "Unable to determine file name for $filePathOrUri" }
            require(size >= 0L) { "Unable to determine file size for $filePathOrUri" }
            return ResolvedFile(uri, name, size, System.currentTimeMillis())
        }

    private fun Cursor.getStringOrNull(column: String): String? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getString(index) else null
        }

    private fun Cursor.getLongOrNull(column: String): Long? {
            val index = getColumnIndex(column)
            return if (index >= 0 && !isNull(index)) getLong(index) else null
        }

    private fun findRoomForEvent(eventId: String): org.matrix.android.sdk.api.session.room.Room? {
            val currentSession = session ?: return null
            timelineCache.entries.firstOrNull { (_, timeline) ->
                timeline.getSnapshot().any { it.eventId == eventId }
            }?.key?.let { return currentSession.roomService().getRoom(it) }
            return currentSession.roomService()
                .getRoomSummaries(
                    roomSummaryQueryParams {},
                    RoomSortOrder.PRIORITY_AND_ACTIVITY
                )
                .asSequence()
                .mapNotNull { summary -> currentSession.roomService().getRoom(summary.roomId) }
                .firstOrNull { room -> room.timelineService().getTimelineEvent(eventId) != null }
    }

    private fun RoomSummary.toChatRoom(): ChatRoom {
        val lastMsg = latestPreviewableEvent
        return ChatRoom(
            roomId = roomId,
            name = displayName,
            topic = topic,
            avatarUrl = avatarUrl,
            lastMessage = lastMsg?.toLastMessage(),
            unreadCount = notificationCount,
            isDirect = isDirect,
            membersCount = joinedMembersCount ?: 0
        )
    }

    private fun TimelineEvent.toMessage(myUserId: String): Message? {
        val sender = senderInfo.displayName ?: senderInfo.userId
        val isMine = senderInfo.userId == myUserId
        
        val type = when {
            root.isTextMessage() -> MessageType.TEXT
            root.isImageMessage() -> MessageType.IMAGE
            root.isVideoMessage() -> MessageType.VIDEO
            root.isAudioMessage() -> MessageType.AUDIO
            root.isFileMessage() -> MessageType.FILE
            else -> MessageType.UNKNOWN
        }

        if (type == MessageType.UNKNOWN) return null

        return Message(
            eventId = eventId,
            senderId = senderInfo.userId,
            senderName = sender,
            body = this.getTextEditableContent(false) ?: "",
            timestamp = root.originServerTs ?: 0,
            messageType = type,
            isMine = isMine,
            isDeleted = root.isRedacted(),
            isEdited = root.content?.get("m.new_content") != null ||
                (root.content?.get("m.relates_to") as? Map<*, *>)?.get("rel_type") == "m.replace"
        )
    }

    private fun TimelineEvent.toLastMessage(): LastMessage {
        val sender = senderInfo.displayName ?: senderInfo.userId

        val type = when {
            root.isTextMessage() -> MessageType.TEXT
            root.isImageMessage() -> MessageType.IMAGE
            root.isVideoMessage() -> MessageType.VIDEO
            root.isAudioMessage() -> MessageType.AUDIO
            root.isFileMessage() -> MessageType.FILE
            else -> MessageType.UNKNOWN
        }

        return LastMessage(
            senderId = senderInfo.userId,
            senderName = sender,
            body = this.getTextEditableContent(false) ?: "Message",
            timestamp = root.originServerTs ?: 0,
            messageType = type
        )
    }

    override fun getCurrentSession(): org.matrix.android.sdk.api.session.Session? {
        return session
    }
}
