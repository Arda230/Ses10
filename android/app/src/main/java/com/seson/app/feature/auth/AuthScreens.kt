package com.seson.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val AuthBackground = Color(0xFF080711)
private val AuthSurface = Color(0xE6161423)
private val AuthSurfaceBorder = Color(0xFF302C43)
private val AuthPrimary = Color(0xFFA99BFF)
private val AuthAccent = Color(0xFF6FE7D4)
private val AuthText = Color(0xFFF5F2FF)
private val AuthMutedText = Color(0xFFAAA4BA)
private val AuthField = Color(0xFF11101B)

@Composable
fun LoginScreen(onLogin: suspend (String, String) -> Result<Unit>, onAuthenticated: () -> Unit, onRegister: () -> Unit) {
    AuthLayout("Tekrar hoş geldin", "Ses10'a devam etmek için giriş yap.") {
        var login by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        AuthTextField(login, { login = it }, "E-posta veya kullanıcı adı")
        AuthTextField(password, { password = it }, "Şifre", isPassword = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        AuthPrimaryButton(
            text = if (busy) "Bağlanıyor..." else "Giriş yap",
            enabled = login.isNotBlank() && password.isNotBlank() && !busy,
            onClick = {
                busy = true; error = null
                scope.launch { onLogin(login, password).onSuccess { onAuthenticated() }.onFailure { error = it.message }; busy = false }
            },
        )
        TextButton(
            onClick = onRegister,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = AuthMutedText, disabledContentColor = AuthMutedText.copy(alpha = 0.45f)),
        ) { Text("Hesap oluştur", fontWeight = FontWeight.Medium) }
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
        AuthTextField(username, { username = it }, "Kullanıcı adı")
        AuthTextField(email, { email = it }, "E-posta")
        AuthTextField(password, { password = it }, "Şifre", isPassword = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        AuthPrimaryButton(
            text = if (busy) "Kaydediliyor..." else "Kayıt ol",
            enabled = username.isNotBlank() && email.isNotBlank() && password.isNotBlank() && !busy,
            onClick = {
                busy = true; error = null
                scope.launch { onRegister(username, email, password).onSuccess { onAuthenticated() }.onFailure { error = it.message }; busy = false }
            },
        )
        TextButton(
            onClick = onBack,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(contentColor = AuthMutedText, disabledContentColor = AuthMutedText.copy(alpha = 0.45f)),
        ) { Text("Girişe dön", fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun AuthTextField(value: String, onValueChange: (String) -> Unit, label: String, isPassword: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AuthText,
            unfocusedTextColor = AuthText,
            focusedContainerColor = AuthField,
            unfocusedContainerColor = AuthField,
            disabledContainerColor = AuthField.copy(alpha = 0.65f),
            cursorColor = AuthAccent,
            focusedBorderColor = AuthAccent,
            unfocusedBorderColor = AuthSurfaceBorder,
            focusedLabelColor = AuthAccent,
            unfocusedLabelColor = AuthMutedText,
        ),
    )
}

@Composable
private fun AuthPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthPrimary,
            contentColor = Color(0xFF120F20),
            disabledContainerColor = AuthPrimary.copy(alpha = 0.28f),
            disabledContentColor = AuthText.copy(alpha = 0.45f),
        ),
    ) { Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
}

@Composable
private fun AuthLayout(title: String, subtitle: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AuthBackground)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "SES10",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp,
                color = AuthText,
            )
            Text(
                text = "Canlı sohbet. Gerçek bağlar.",
                style = MaterialTheme.typography.bodyMedium,
                color = AuthMutedText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).border(1.dp, AuthSurfaceBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AuthSurface),
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = AuthText)
                    Text(subtitle, color = AuthMutedText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    content()
                }
            }
        }
    }
}
