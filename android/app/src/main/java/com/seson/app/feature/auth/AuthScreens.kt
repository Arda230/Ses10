package com.seson.app.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLogin: suspend (String, String) -> Result<Unit>, onAuthenticated: () -> Unit, onRegister: () -> Unit) {
    AuthLayout("Tekrar hoş geldin", "Ses10'a devam etmek için giriş yap.") {
        var login by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        OutlinedTextField(login, { login = it }, label = { Text("E-posta veya kullanıcı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = {
            busy = true; error = null
            scope.launch { onLogin(login, password).onSuccess { onAuthenticated() }.onFailure { error = it.message }; busy = false }
        }, enabled = login.isNotBlank() && password.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Bağlanıyor..." else "Giriş yap") }
        TextButton(onClick = onRegister, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Hesap oluştur") }
    }
}

@Composable
fun RegisterScreen(onRegister: suspend (String, String, String) -> Result<Unit>, onAuthenticated: () -> Unit, onBack: () -> Unit) {
    AuthLayout("Sesini duyur", "Topluluğa katılmak için hesabını oluştur.") {
        var username by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        OutlinedTextField(username, { username = it }, label = { Text("Kullanıcı adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("E-posta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("Şifre") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Button(onClick = {
            busy = true; error = null
            scope.launch { onRegister(username, email, password).onSuccess { onAuthenticated() }.onFailure { error = it.message }; busy = false }
        }, enabled = username.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Kaydediliyor..." else "Kayıt ol") }
        TextButton(onClick = onBack, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Girişe dön") }
    }
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("SES10", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Card(Modifier.fillMaxWidth().widthIn(max = 480.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                content()
            }
        }
    }
}
