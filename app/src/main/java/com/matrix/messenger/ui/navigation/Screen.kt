package com.matrix.messenger.ui.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Chat : Screen("chat/{roomId}") {
        fun createRoute(roomId: String) = "chat/${Uri.encode(roomId)}"
    }
}
