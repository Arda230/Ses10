package com.seson.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seson.app.ui.components.ScreenScaffold

@Composable
fun SettingsScreen(onBack: () -> Unit, onLogout: () -> Unit) {
    ScreenScaffold(title = "Ayarlar", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Bildirimler (yakında)")
            HorizontalDivider()
            Text("Gizlilik (yakında)")
            HorizontalDivider()
            Text("Ses ve mikrofon (yakında)")
            Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Çıkış yap") }
        }
    }
}
