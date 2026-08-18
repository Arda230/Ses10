package com.seson.app.navigation

sealed class Destination(val route: String) {
    data object Splash : Destination("splash")
    data object Login : Destination("login")
    data object Register : Destination("register")
    data object Home : Destination("home")
    data object Room : Destination("room/{roomName}")
}
