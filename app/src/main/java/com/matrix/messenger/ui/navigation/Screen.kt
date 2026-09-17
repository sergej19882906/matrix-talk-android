package com.matrix.messenger.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    
    object Chat : Screen("chat/{roomId}/{peerUserId}/{peerName}/{peerAvatarUrl}") {
        fun createRoute(
            roomId: String,
            peerUserId: String,
            peerName: String,
            peerAvatarUrl: String
        ): String {
            return "chat/$roomId/$peerUserId/$peerName/$peerAvatarUrl"
        }
    }
    
    // 🆕 Маршрут для экрана звонка
    object Call : Screen("call/{callId}/{roomId}/{peerUserId}/{peerName}/{peerAvatarUrl}/{isVideo}/{isIncoming}") {
        fun createRoute(
            callId: String,
            roomId: String,
            peerUserId: String,
            peerName: String,
            peerAvatarUrl: String,
            isVideo: Boolean,
            isIncoming: Boolean
        ): String {
            return "call/$callId/$roomId/$peerUserId/$peerName/$peerAvatarUrl/$isVideo/$isIncoming"
        }
    }
}