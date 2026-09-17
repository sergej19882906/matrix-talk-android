package com.matrix.messenger.data.repository

import com.matrix.messenger.data.model.CallSession
import com.matrix.messenger.data.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.matrix.android.sdk.api.session.Session
import org.matrix.android.sdk.api.session.call.CallState as MatrixCallState
import org.matrix.android.sdk.api.session.call.MxPeer
import org.matrix.android.sdk.api.session.call.TurnServerResponse
import org.matrix.android.sdk.api.session.events.Event
import org.matrix.android.sdk.api.session.events.EventType
import org.matrix.android.sdk.api.session.events.toModel
import org.matrix.android.sdk.api.session.room.model.call.AcceptedCallContent
import org.matrix.android.sdk.api.session.room.model.call.BaseCallContent
import org.matrix.android.sdk.api.session.room.model.call.CallAnswerContent
import org.matrix.android.sdk.api.session.room.model.call.CallCandidatesContent
import org.matrix.android.sdk.api.session.room.model.call.CallHangupContent
import org.matrix.android.sdk.api.session.room.model.call.CallInviteContent
import org.matrix.android.sdk.api.session.room.model.call.CallRejectContent
import org.matrix.android.sdk.api.session.room.model.call.EndCallReason
import org.matrix.android.sdk.api.session.room.model.call.SessionDescription
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallRepository @Inject constructor(
    private val session: Session
) {
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private var currentCall: CallSession? = null
    private var currentCallId: String? = null

    // WebRTC компоненты (будут инициализированы позже)
    private var peerConnectionFactory: org.webrtc.PeerConnectionFactory? = null
    private var localPeer: org.webrtc.PeerConnection? = null
    private var remotePeer: org.webrtc.PeerConnection? = null
    private var localAudioTrack: org.webrtc.AudioTrack? = null
    private var localVideoTrack: org.webrtc.VideoTrack? = null

    init {
        initializeWebRTC()
        setupCallListeners()
    }

    /**
     * Инициализация WebRTC
     */
    private fun initializeWebRTC() {
        // Инициализация WebRTC (упрощённая версия)
        // В реальном приложении нужна полная настройка с ICE servers, STUN/TURN
        org.webrtc.PeerConnectionFactory.initialize(
            org.webrtc.PeerConnectionFactory.InitializationOptions.builder(android.app.Application())
                .createInitializationOptions()
        )
        
        peerConnectionFactory = org.webrtc.PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }

    /**
     * Настройка слушателей событий звонков от Matrix SDK
     */
    private fun setupCallListeners() {
        // Слушатель входящих звонков
        session.callSignalingService().addCallListener(object : org.matrix.android.sdk.api.session.call.CallListener {
            override fun onIncomingInvite(callInviteContent: CallInviteContent, event: Event) {
                val callId = callInviteContent.callId
                val callerId = event.senderId ?: return
                val callerName = session.userService().getUser(callerId)?.displayName ?: "Неизвестный"
                val isVideo = callInviteContent.offer?.sdp?.contains("m=video") == true

                currentCallId = callId
                currentCall = CallSession(
                    callId = callId,
                    roomId = event.roomId ?: "",
                    peerUserId = callerId,
                    peerDisplayName = callerName,
                    isVideo = isVideo,
                    startTime = System.currentTimeMillis()
                )

                _callState.value = CallState.Incoming(callerId, callerName)
            }

            override fun onCallEnded(callId: String, reason: EndCallReason?) {
                if (currentCallId == callId) {
                    _callState.value = CallState.Ended(reason?.name ?: "Завершено")
                    cleanupCall()
                }
            }

            override fun onCallAnswered(callId: String) {
                if (currentCallId == callId) {
                    currentCall?.let { call ->
                        _callState.value = CallState.Connected(call.peerUserId, call.peerDisplayName)
                    }
                }
            }

            override fun onCallRejected(callId: String) {
                if (currentCallId == callId) {
                    _callState.value = CallState.Ended("Отклонено")
                    cleanupCall()
                }
            }

            override fun onCallHangup(callId: String, reason: EndCallReason?) {
                if (currentCallId == callId) {
                    _callState.value = CallState.Ended(reason?.name ?: "Завершено")
                    cleanupCall()
                }
            }
        })
    }

    /**
     * Начать исходящий звонок
     */
    suspend fun startCall(roomId: String, peerUserId: String, isVideo: Boolean) {
        val room = session.roomService().getRoom(roomId)
            ?: throw IllegalStateException("Комната не найдена: $roomId")

        val peerName = session.userService().getUser(peerUserId)?.displayName ?: "Неизвестный"

        // Создаём SDP offer
        val sdp = createLocalSdp(isVideo)
        
        // Отправляем invite через Matrix SDK
        val callId = room.callService().startCall(
            isVideoCall = isVideo,
            otherUserId = peerUserId
        )

        currentCallId = callId
        currentCall = CallSession(
            callId = callId,
            roomId = roomId,
            peerUserId = peerUserId,
            peerDisplayName = peerName,
            isVideo = isVideo,
            startTime = System.currentTimeMillis()
        )

        _callState.value = CallState.Outgoing(peerUserId, peerName)

        // Инициализируем WebRTC соединение
        initializePeerConnection(isVideo)
    }

    /**
     * Принять входящий звонок
     */
    suspend fun acceptCall(callId: String? = null) {
        val targetCallId = callId ?: currentCallId
            ?: throw IllegalStateException("Нет активного звонка")

        val call = currentCall ?: throw IllegalStateException("Нет информации о звонке")

        // Отправляем answer через Matrix SDK
        session.roomService().getRoom(call.roomId)?.callService()?.acceptCall(targetCallId)

        // Создаём SDP answer
        val sdp = createLocalSdp(call.isVideo)

        _callState.value = CallState.Connected(call.peerUserId, call.peerDisplayName)

        // Инициализируем WebRTC соединение
        initializePeerConnection(call.isVideo)
    }

    /**
     * Отклонить входящий звонок
     */
    suspend fun rejectCall(callId: String? = null) {
        val targetCallId = callId ?: currentCallId ?: return

        currentCall?.let { call ->
            session.roomService().getRoom(call.roomId)?.callService()?.rejectCall(targetCallId)
        }

        _callState.value = CallState.Idle
        cleanupCall()
    }

    /**
     * Завершить звонок
     */
    suspend fun endCall(callId: String? = null) {
        val targetCallId = callId ?: currentCallId ?: return

        currentCall?.let { call ->
            session.roomService().getRoom(call.roomId)?.callService()?.endCall(targetCallId)
        }

        _callState.value = CallState.Ended("Завершено")
        cleanupCall()
    }

    /**
     * Переключить микрофон (mute/unmute)
     */
    fun toggleMute(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    /**
     * Переключить видео (включить/выключить)
     */
    fun toggleVideo(isEnabled: Boolean) {
        localVideoTrack?.setEnabled(isEnabled)
    }

    /**
     * Переключить камеру (фронтальная/задняя)
     */
    fun switchCamera() {
        // Реализация переключения камеры через WebRTC
        // В реальном приложении нужно использовать VideoCapturer
    }

    /**
     * Получить ID текущего звонка
     */
    fun getCurrentCallId(): String? = currentCallId

    /**
     * Создать локальный SDP (упрощённая версия)
     */
    private fun createLocalSdp(isVideo: Boolean): String {
        // В реальном приложении SDP создаётся через WebRTC PeerConnection
        return if (isVideo) {
            """
            v=0
            o=- ${System.currentTimeMillis()} 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=rtpmap:111 opus/48000/2
            a=sendrecv
            m=video 9 UDP/TLS/RTP/SAVPF 96
            c=IN IP4 0.0.0.0
            a=rtpmap:96 VP8/90000
            a=sendrecv
            """.trimIndent()
        } else {
            """
            v=0
            o=- ${System.currentTimeMillis()} 2 IN IP4 127.0.0.1
            s=-
            t=0 0
            m=audio 9 UDP/TLS/RTP/SAVPF 111
            c=IN IP4 0.0.0.0
            a=rtpmap:111 opus/48000/2
            a=sendrecv
            """.trimIndent()
        }
    }

    /**
     * Инициализировать PeerConnection для WebRTC
     */
    private fun initializePeerConnection(isVideo: Boolean) {
        // Упрощённая версия - в реальном приложении нужна полная настройка
        val rtcConfig = org.webrtc.PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = org.webrtc.PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        localPeer = peerConnectionFactory?.createPeerConnection(rtcConfig, object : org.webrtc.PeerConnection.Observer {
            override fun onSignalingChange(newState: org.webrtc.PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: org.webrtc.PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: org.webrtc.PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out org.webrtc.IceCandidate>?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(rtpReceiver: org.webrtc.RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) {}
        })

        // Создаём локальные медиа-треки
        val audioConstraints = org.webrtc.MediaConstraints().apply {
            mandatory.add(org.webrtc.MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        }
        localAudioTrack = peerConnectionFactory?.createAudioTrack("audio", 
            peerConnectionFactory?.createAudioSource(audioConstraints))

        if (isVideo) {
            val videoCapturer = org.webrtc.Camera2Enumerator(android.app.Application()).run {
                deviceNames.firstOrNull()?.let { createCapturer(it, null) }
            }
            val videoSource = peerConnectionFactory?.createVideoSource(false)
            videoCapturer?.let { capturer ->
                capturer.initialize(
                    org.webrtc.SurfaceTextureHelper.create("CaptureThread", null),
                    android.app.Application(),
                    videoSource?.capturerObserver
                )
                capturer.startCapture(640, 480, 30)
            }
            localVideoTrack = peerConnectionFactory?.createVideoTrack("video", videoSource)
        }

        // Добавляем треки к PeerConnection
        localAudioTrack?.let { track ->
            localPeer?.addTrack(track)
        }
        localVideoTrack?.let { track ->
            localPeer?.addTrack(track)
        }
    }

    /**
     * Очистка ресурсов после завершения звонка
     */
    private fun cleanupCall() {
        localPeer?.close()
        remotePeer?.close()
        localAudioTrack?.dispose()
        localVideoTrack?.dispose()
        
        localPeer = null
        remotePeer = null
        localAudioTrack = null
        localVideoTrack = null
        currentCall = null
        currentCallId = null
    }

    /**
     * Освобождение ресурсов при уничтожении репозитория
     */
    fun release() {
        cleanupCall()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
}