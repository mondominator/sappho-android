package com.sappho.audiobooks.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sappho.audiobooks.data.repository.AuthRepository
import com.sappho.audiobooks.presentation.login.LoginScreen
import com.sappho.audiobooks.presentation.main.MainScreen
import javax.inject.Inject

@Composable
fun SapphoApp(
    authRepository: AuthRepository = hiltViewModel<SapphoAppViewModel>().authRepository,
    initialAuthor: String? = null,
    initialSeries: String? = null
) {
    val navController = rememberNavController()
    val isAuthenticated by authRepository.isAuthenticated.collectAsState()

    val startDestination = if (isAuthenticated) "main" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("main") {
                        popUpTo("login") { inclusive = true }
                    }
                }
            )
        }

        composable("main") {
            MainScreen(
                onLogout = {
                    authRepository.clearToken()
                    navController.navigate("login") {
                        popUpTo(0) { inclusive = true }
                    }
                },
                initialAuthor = initialAuthor,
                initialSeries = initialSeries
            )
        }
    }
}

// ViewModel to provide AuthRepository to SapphoApp
@dagger.hilt.android.lifecycle.HiltViewModel
class SapphoAppViewModel @Inject constructor(
    val authRepository: AuthRepository
) : androidx.lifecycle.ViewModel()
