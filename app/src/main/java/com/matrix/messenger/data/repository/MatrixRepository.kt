package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.ChatRoom
import com.matrix.messenger.data.model.ConnectionState
import com.matrix.messenger.data.model.LastMessage
import com.matrix.messenger.data.model.LoginResult
import com.matrix.messenger.data.model.MatrixUser
import com.matrix.messenger.data.model.Message
import com.matrix.messenger.data.model.MessageType
import kotlinx.coroutines.flow.Flow

/**
 * Репозиторий для работы с Matrix SDK
 */
interface MatrixRepository {

    /**
     * Поток текущего пользователя
     */
    val currentUser: Flow<MatrixUser?>

    /**
     * Поток состояния подключения
     */
    val connectionState: Flow<ConnectionState>

    /**
     * Инициализация SDK
     */
    suspend fun initialize()

    /**
     * Вход по логину и паролю
     */
    suspend fun login(
        homeServer: String,
        username: String,
        password: String
    ): LoginResult

    /**
     * Вход по токену
     */
    suspend fun loginWithToken(
        homeServer: String,
        userId: String,
        accessToken: String
    ): LoginResult

    /**
     * Выход из аккаунта
     */
    suspend fun logout()

    /**
     * Поток списка комнат
     */
    fun getRoomsFlow(): Flow<List<ChatRoom>>

    /**
     * Поток сообщений комнаты
     */
    fun getMessagesFlow(roomId: String): Flow<List<Message>>

    /**
     * Отправить текстовое сообщение
     */
    suspend fun sendTextMessage(roomId: String, text: String)

    /**
     * Отправить сообщение с файлом
     */
    suspend fun sendFileMessage(
        roomId: String,
        filePath: String,
        mimeType: String,
        caption: String? = null
    )

    /**
     * Получить информацию о комнате
     */
    suspend fun getRoomInfo(roomId: String): ChatRoom?

    /**
     * Создать новую комнату
     */
    suspend fun createRoom(
        name: String?,
        topic: String?,
        isDirect: Boolean,
        userIds: List<String>
    ): String

    /**
     * Присоединиться к комнате по алиасу
     */
    suspend fun joinRoom(aliasOrId: String): String

    /**
     * Покинуть комнату
     */
    suspend fun leaveRoom(roomId: String)

    /**
     * Отправить прочитанное событие
     */
    suspend fun markRoomAsRead(roomId: String)

    /**
     * Напечатать индикатор набора текста
     */
    suspend fun sendTypingNotification(roomId: String, isTyping: Boolean)

    /**
     * Реагировать на сообщение
     */
    suspend fun sendReaction(eventId: String, reaction: String)

    /**
     * Редактировать сообщение
     */
    suspend fun editMessage(eventId: String, roomId: String, newText: String)

    /**
     * Удалить сообщение
     */
    suspend fun deleteMessage(eventId: String, roomId: String)

    /**
     * Загрузить аватар пользователя
     */
    suspend fun uploadAvatar(filePath: String)

    /**
     * Установить отображаемое имя
     */
    suspend fun setDisplayName(name: String)
}
