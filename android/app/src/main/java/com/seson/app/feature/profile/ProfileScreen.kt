package com.seson.app.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seson.app.ui.components.ScreenScaffold

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Profil", onBack = onBack) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SK", style = MaterialTheme.typography.displayMedium)
            Text("Ses10 Kullanıcısı", style = MaterialTheme.typography.headlineSmall)
            Text("Profil bilgileri gerçek auth bağlantısından sonra gösterilecek.")
        }
    }
}
