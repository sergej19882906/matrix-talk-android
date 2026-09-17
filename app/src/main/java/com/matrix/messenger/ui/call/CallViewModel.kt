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

    init {
        viewModelScope.launch {
            callRepository.callState.collect { state ->
                _callState.value = state
                when (state) {
                    is CallState.Connected -> startCallTimer()
                    is CallState.Ended, CallState.Idle -> stopCallTimer()
                    else -> { /* ignore */ }
                }
            }
        }
    }

    fun startCall(roomId: String, peerUserId: String, peerName: String, isVideo: Boolean) {
        viewModelScope.launch {
            try {
                callRepository.startCall(roomId, peerUserId, isVideo)
                val callId = callRepository.getCurrentCallId() ?: return@launch
                CallService.startOutgoingCall(context, callId, roomId, peerName, isVideo)
            } catch (e: Exception) {
                _callState.value = CallState.Ended("Ошибка: ${e.message}")
            }
        }
    }

    fun acceptCall(callId: String, roomId: String, peerName: String, isVideo: Boolean) {
        viewModelScope.launch {
            try {
                callRepository.acceptCall(callId)
                CallService.startOutgoingCall(context, callId, roomId, peerName, isVideo)
            } catch (e: Exception) {
                _callState.value = CallState.Ended("Ошибка: ${e.message}")
            }
        }
    }

    fun rejectCall() {
        viewModelScope.launch {
            callRepository.rejectCall()
        }
    }

    fun endCall() {
        viewModelScope.launch {
            callRepository.endCall()
            CallService.endCall(context)
        }
    }

    fun toggleMute() {
        val newMutedState = !_isMuted.value
        _isMuted.value = newMutedState
        callRepository.toggleMute(newMutedState)
    }

    fun toggleVideo() {
        val newVideoState = !_isVideoEnabled.value
        _isVideoEnabled.value = newVideoState
        callRepository.toggleVideo(newVideoState)
    }

    fun switchCamera() {
        callRepository.switchCamera()
    }

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

    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
        _callDuration.value = 0L
    }

    fun formatDuration(durationSeconds: Long): String {
        val minutes = durationSeconds / 60
        val seconds = durationSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onCleared() {
        super.onCleared()
        callTimerJob?.cancel()
    }
}