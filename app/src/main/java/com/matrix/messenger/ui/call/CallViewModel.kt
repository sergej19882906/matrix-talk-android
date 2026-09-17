package com.matrix.messenger.ui.call

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.matrix.messenger.data.model.CallState
import com.matrix.messenger.data.repository.CallRepository
import com.matrix.messenger.service.CallService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val callRepository: CallRepository
) : ViewModel() {

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isVideoEnabled = MutableStateFlow(true)
    val isVideoEnabled: StateFlow<Boolean> = _isVideoEnabled.asStateFlow()

    private val _callDuration = MutableStateFlow(0L)
    val callDuration: StateFlow<Long> = _callDuration.asStateFlow()

    private var callTimerJob: Job? = null
    private var callStartTime: Long = 0L

    // Текущая информация о звонке
    private var currentCallId: String? = null
    private var currentRoomId: String? = null
    private var currentPeerName: String? = null
    private var currentIsVideo: Boolean = true

    init {
        // Наблюдаем за состоянием звонка из репозитория
        viewModelScope.launch {
            callRepository.callState.collect { state ->
                _callState.value = state
                
                // Запускаем/останавливаем таймер в зависимости от состояния
                when (state) {
                    is CallState.Connected -> {
                        startCallTimer()
                    }
                    is CallState.Ended, CallState.Idle -> {
                        stopCallTimer()
                    }
                    else -> { /* ignore */ }
                }
            }
        }
    }

    /**
     * Начать исходящий звонок
     */
    fun startCall(roomId: String, peerUserId: String, peerName: String, isVideo: Boolean) {
        viewModelScope.launch {
            currentRoomId = roomId
            currentPeerName = peerName
            currentIsVideo = isVideo

            try {
                callRepository.startCall(roomId, peerUserId, isVideo)
                
                // Получаем callId из репозитория (предполагается, что он сохраняется)
                currentCallId = callRepository.getCurrentCallId()
                
                // Запускаем сервис для foreground уведомления
                currentCallId?.let { callId ->
                    CallService.startOutgoingCall(
                        context = context,
                        callId = callId,
                        roomId = roomId,
                        peerName = peerName,
                        isVideo = isVideo
                    )
                }
            } catch (e: Exception) {
                // Обработка ошибки
                _callState.value = CallState.Ended("Ошибка: ${e.message}")
            }
        }
    }

    /**
     * Принять входящий звонок
     */
    fun acceptCall(callId: String, roomId: String, peerName: String, isVideo: Boolean) {
        viewModelScope.launch {
            currentCallId = callId
            currentRoomId = roomId
            currentPeerName = peerName
            currentIsVideo = isVideo

            try {
                callRepository.acceptCall(callId)
                
                // Обновляем сервис - звонок теперь активный
                CallService.startOutgoingCall(
                    context = context,
                    callId = callId,
                    roomId = roomId,
                    peerName = peerName,
                    isVideo = isVideo
                )
            } catch (e: Exception) {
                _callState.value = CallState.Ended("Ошибка: ${e.message}")
            }
        }
    }

    /**
     * Отклонить входящий звонок
     */
    fun rejectCall() {
        viewModelScope.launch {
            try {
                callRepository.rejectCall()
                stopSelf()
            } catch (e: Exception) {
                // Логируем ошибку
            }
        }
    }

    /**
     * Завершить звонок
     */
    fun endCall() {
        viewModelScope.launch {
            try {
                callRepository.endCall()
                CallService.endCall(context)
            } catch (e: Exception) {
                // Логируем ошибку
            }
        }
    }

    /**
     * Переключить микрофон (mute/unmute)
     */
    fun toggleMute() {
        viewModelScope.launch {
            val newMutedState = !_isMuted.value
            _isMuted.value = newMutedState
            
            try {
                callRepository.toggleMute(newMutedState)
            } catch (e: Exception) {
                // Возвращаем предыдущее состояние при ошибке
                _isMuted.value = !newMutedState
            }
        }
    }

    /**
     * Переключить камеру (включить/выключить видео)
     */
    fun toggleVideo() {
        viewModelScope.launch {
            val newVideoState = !_isVideoEnabled.value
            _isVideoEnabled.value = newVideoState
            
            try {
                callRepository.toggleVideo(newVideoState)
            } catch (e: Exception) {
                // Возвращаем предыдущее состояние при ошибке
                _isVideoEnabled.value = !newVideoState
            }
        }
    }

    /**
     * Переключить камеру (фронтальная/задняя)
     */
    fun switchCamera() {
        viewModelScope.launch {
            try {
                callRepository.switchCamera()
            } catch (e: Exception) {
                // Логируем ошибку
            }
        }
    }

    /**
     * Запустить таймер звонка
     */
    private fun startCallTimer() {
        callStartTime = System.currentTimeMillis()
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                _callDuration.value = (System.currentTimeMillis() - callStartTime) / 1000
                delay(1000)
            }
        }
    }

    /**
     * Остановить таймер звонка
     */
    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
        _callDuration.value = 0L
    }

    /**
     * Форматировать длительность звонка в строку MM:SS
     */
    fun formatDuration(durationSeconds: Long): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * Очистка ресурсов при уничтожении ViewModel
     */
    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
    }

    private fun stopSelf() {
        // Этот метод вызывается из сервиса, здесь просто заглушка
    }
}