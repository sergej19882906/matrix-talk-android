package com.matrix.messenger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.matrix.messenger.ui.chat.ChatScreen
import com.matrix.messenger.ui.home.HomeScreen
import com.matrix.messenger.ui.login.LoginScreen
import java.net.URLDecoder
import java.net.URLEncoder

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String = "login"
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Экран входа
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        // Главный экран (список чатов)
        composable("home") {
            HomeScreen(
                onChatClick = { roomId, peerUserId, peerName, peerAvatarUrl ->
                    // Кодируем параметры для безопасной передачи через URL
                    val encodedName = URLEncoder.encode(peerName, "UTF-8")
                    val encodedAvatar = URLEncoder.encode(peerAvatarUrl ?: "", "UTF-8")
                    navController.navigate("chat/$roomId/$peerUserId/$encodedName/$encodedAvatar")
                }
            )
        }

        // Экран чата
        composable(
            route = "chat/{roomId}/{peerUserId}/{peerName}/{peerAvatarUrl}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("peerUserId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType },
                navArgument("peerAvatarUrl") { 
                    type = NavType.StringType
                    nullable = true
                    // Убрали defaultValue = "", так как он вызывает ошибку вывода типов в Nav 2.8.x
                }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val peerUserId = backStackEntry.arguments?.getString("peerUserId") ?: ""
            val peerNameEncoded = backStackEntry.arguments?.getString("peerName") ?: ""
            val peerAvatarUrlEncoded = backStackEntry.arguments?.getString("peerAvatarUrl")

            // Безопасное декодирование без цепочек let, чтобы не сбивать компилятор
            val peerName = try {
                URLDecoder.decode(peerNameEncoded, "UTF-8")
            } catch (e: Exception) {
                peerNameEncoded
            }

            val peerAvatarUrl = if (!peerAvatarUrlEncoded.isNullOrEmpty()) {
                try {
                    URLDecoder.decode(peerAvatarUrlEncoded, "UTF-8")
                } catch (e: Exception) {
                    peerAvatarUrlEncoded
                }
            } else {
                null
            }

            ChatScreen(
                roomId = roomId,
                peerUserId = peerUserId,
                peerName = peerName,
                peerAvatarUrl = peerAvatarUrl,
                navController = navController
            )
        }
    }
}