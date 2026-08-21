package com.seson.app.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.seson.app.core.network.Ses10Api
import com.seson.app.feature.auth.LoginScreen
import com.seson.app.feature.auth.RegisterScreen
import com.seson.app.feature.home.HomeScreen
import com.seson.app.feature.room.RoomScreen
import com.seson.app.feature.splash.SplashScreen
import kotlinx.coroutines.launch

@Composable
fun Ses10App() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val openHome = {
        navController.navigate(Destination.Home.route) {
            popUpTo(Destination.Login.route) { inclusive = true }
        }
    }
    NavHost(navController = navController, startDestination = Destination.Splash.route) {
        composable(Destination.Splash.route) {
            SplashScreen {
                scope.launch {
                    val target = if (Ses10Api.me().getOrNull() != null) Destination.Home.route else Destination.Login.route
                    navController.navigate(target) { popUpTo(Destination.Splash.route) { inclusive = true } }
                }
            }
        }
        composable(Destination.Login.route) {
            LoginScreen(
                onLogin = Ses10Api::login,
                onAuthenticated = openHome,
                onRegister = { navController.navigate(Destination.Register.route) },
            )
        }
        composable(Destination.Register.route) {
            RegisterScreen(
                onRegister = Ses10Api::register,
                onAuthenticated = openHome,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destination.Home.route) {
            HomeScreen(onOpenRoom = { roomName -> navController.navigate("room/${Uri.encode(roomName)}") }, onLogout = {
                scope.launch {
                    Ses10Api.logout()
                    navController.navigate(Destination.Login.route) { popUpTo(Destination.Home.route) { inclusive = true } }
                }
            })
        }
        composable(Destination.Room.route, arguments = listOf(navArgument("roomName") { type = NavType.StringType })) { entry ->
            RoomScreen(roomName = requireNotNull(entry.arguments?.getString("roomName")), onBack = { navController.popBackStack() })
        }
    }
}
