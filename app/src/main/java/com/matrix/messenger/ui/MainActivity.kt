package com.matrix.messenger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.matrix.messenger.data.repository.MatrixRepository
import com.matrix.messenger.ui.navigation.AppNavigation
import com.matrix.messenger.ui.navigation.Screen
import com.matrix.messenger.ui.theme.MatrixMessengerTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var matrixRepository: MatrixRepository

    private var isReady by mutableStateOf(false)
    private var isAuthenticated by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition {
            !isReady
        }

        lifecycleScope.launch {
            matrixRepository.initialize()
            isAuthenticated = matrixRepository.currentUser.first() != null
            isReady = true
        }

        setContent {
            MatrixMessengerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    if (isReady) {
                        AppNavigation(
                            navController = navController,
                            startDestination = if (isAuthenticated) {
                                Screen.Home.route
                            } else {
                                Screen.Login.route
                            }
                        )
                    }
                }
            }
        }
    }
}
