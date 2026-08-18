package com.seson.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.seson.app.navigation.Ses10App
import com.seson.app.ui.theme.Ses10Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Ses10Theme { Ses10App() } }
    }
}
