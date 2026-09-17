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
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Экран входа
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Главный экран (список чатов)
        composable(Screen.Home.route) {
            HomeScreen(
                onChatClick = { roomId, peerUserId, peerName, peerAvatarUrl ->
                    // Кодируем параметры для безопасной передачи через URL
                    val encodedPeerName = URLEncoder.encode(peerName, "UTF-8")
                    val encodedAvatarUrl = peerAvatarUrl?.let { URLEncoder.encode(it, "UTF-8") } ?: ""
                    
                    navController.navigate(
                        Screen.Chat.createRoute(
                            roomId = roomId,
                            peerUserId = peerUserId,
                            peerName = encodedPeerName,
                            peerAvatarUrl = encodedAvatarUrl
                        )
                    )
                }
            )
        }

        // Экран чата
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("peerUserId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType },
                navArgument("peerAvatarUrl") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val peerUserId = backStackEntry.arguments?.getString("peerUserId") ?: return@composable
            val peerName = backStackEntry.arguments?.getString("peerName")?.let { 
                URLDecoder.decode(it, "UTF-8") 
            } ?: "Неизвестный"
            val peerAvatarUrl = backStackEntry.arguments?.getString("peerAvatarUrl")?.let { 
                if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
            }

            ChatScreen(
                roomId = roomId,
                peerUserId = peerUserId,
                peerName = peerName,
                peerAvatarUrl = peerAvatarUrl,
                navController = navController
            )
        }

        // 🆕 Экран звонка (опционально, если хотите открывать в том же приложении)
        composable(
            route = Screen.Call.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("roomId") { type = NavType.StringType },
                navArgument("peerUserId") { type = NavType.StringType },
                navArgument("peerName") { type = NavType.StringType },
                navArgument("peerAvatarUrl") { 
                    type = NavType.StringType
                    nullable = true
                    defaultValue = ""
                },
                navArgument("isVideo") { type = NavType.BoolType },
                navArgument("isIncoming") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
            val peerUserId = backStackEntry.arguments?.getString("peerUserId") ?: ""
            val peerName = backStackEntry.arguments?.getString("peerName")?.let { 
                URLDecoder.decode(it, "UTF-8") 
            } ?: "Неизвестный"
            val peerAvatarUrl = backStackEntry.arguments?.getString("peerAvatarUrl")?.let { 
                if (it.isNotEmpty()) URLDecoder.decode(it, "UTF-8") else null
            }
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false
            val isIncoming = backStackEntry.arguments?.getBoolean("isIncoming") ?: false

            // Здесь можно использовать CallScreen напрямую, если не хотите отдельную Activity
            // com.matrix.messenger.ui.call.CallScreen(
            //     roomId = roomId,
            //     peerUserId = peerUserId,
            //     peerName = peerName,
            //     peerAvatarUrl = peerAvatarUrl,
            //     isVideo = isVideo,
            //     isOutgoing = !isIncoming,
            //     onCallEnded = { navController.popBackStack() }
            // )
        }
    }
}