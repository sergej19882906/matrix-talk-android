package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.CallSession
import com.matrix.messenger.data.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepository @Inject constructor(
    private val matrixRepository: MatrixRepository
) {
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var currentCall: CallSession? = null
    private var currentCallId: String? = null

    suspend fun startCall(roomId: String, peerUserId: String, isVideo: Boolean) {
        val session = matrixRepository.getCurrentSession() ?: throw IllegalStateException("Пользователь не авторизован")
        val room = session.roomService().getRoom(roomId)
            ?: throw IllegalStateException("Комната не найдена: $roomId")

        val peerName = session.userService().getUser(peerUserId)?.displayName ?: "Неизвестный"

        currentCallId = "call_${System.currentTimeMillis()}"
        currentCall = CallSession(
            callId = currentCallId!!,
            roomId = roomId,
            peerUserId = peerUserId,
            peerDisplayName = peerName,
            isVideo = isVideo,
            startTime = System.currentTimeMillis()
        )
        _callState.value = CallState.Outgoing(peerUserId, peerName)
    }

    suspend fun acceptCall(callId: String? = null) {
        val targetCallId = callId ?: currentCallId ?: return
        currentCall?.let {
            _callState.value = CallState.Connected(it.peerUserId, it.peerDisplayName)
        }
    }

    suspend fun rejectCall(callId: String? = null) {
        _callState.value = CallState.Idle
        currentCall = null
        currentCallId = null
    }

    suspend fun endCall(callId: String? = null) {
        _callState.value = CallState.Ended("Завершено")
        currentCall = null
        currentCallId = null
    }

    fun toggleMute(isMuted: Boolean) { /* Заглушка для компиляции */ }
    fun toggleVideo(isEnabled: Boolean) { /* Заглушка для компиляции */ }
    fun switchCamera() { /* Заглушка для компиляции */ }
    fun getCurrentCallId(): String? = currentCallId
}