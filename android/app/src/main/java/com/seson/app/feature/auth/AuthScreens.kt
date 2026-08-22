package com.seson.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
fun LoginScreen(
    onLogin: suspend (String, String) -> Result<Unit>,
    onAuthenticated: () -> Unit,
    onRegister: () -> Unit,
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LoginLayout(title = "Tekrar hoş geldin!", subtitle = "Hesabına giriş yap ve kaldığın yerden devam et.") {
        LoginTextField(
            value = login,
            onValueChange = { login = it },
            label = "E-posta veya kullanıcı adı",
            symbol = "✉",
        )
        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = "Şifre",
            symbol = "●",
            isPassword = true,
            passwordVisible = passwordVisible,
            onTogglePassword = { passwordVisible = !passwordVisible },
        )
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        LoginPrimaryButton(
            text = if (busy) "Bağlanıyor..." else "Giriş yap",
            enabled = login.isNotBlank() && password.isNotBlank() && !busy,
            onClick = {
                busy = true
                error = null
                scope.launch {
                    onLogin(login, password)
                        .onSuccess { onAuthenticated() }
                        .onFailure { error = it.message }
                    busy = false
                }
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Ses10'da yeni misin?", color = LoginMuted, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRegister, enabled = !busy) {
                Text("Hesap oluştur", color = LoginPurple, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private val LoginBackground = Color(0xFFFFF9FC)
private val LoginSoftPink = Color(0xFFFCEAF5)
private val LoginLavender = Color(0xFFEEE5FF)
private val LoginPurple = Color(0xFFA84FEA)
private val LoginDeepPurple = Color(0xFF7B4ED8)
private val LoginPink = Color(0xFFF062C0)
private val LoginInk = Color(0xFF29232E)
private val LoginMuted = Color(0xFF8D8492)

@Composable
private fun LoginLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(LoginBackground, Color(0xFFFFF5FA), LoginLavender.copy(alpha = .62f))),
        ),
    ) {
        Box(
            Modifier.size(260.dp).offset(x = (-105).dp, y = (-90).dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(LoginPink.copy(.18f), Color.Transparent))),
        )
        Box(
            Modifier.size(300.dp).align(Alignment.BottomEnd).offset(x = 130.dp, y = 115.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(LoginPurple.copy(.16f), Color.Transparent))),
        )

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier.size(88.dp).shadow(12.dp, CircleShape).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(LoginPurple, LoginPink))),
                contentAlignment = Alignment.Center,
            ) {
                Text("🎙", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(13.dp))
            Text("Ses10", color = LoginInk, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text("Canlı sohbet. Gerçek bağlar.", color = LoginMuted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).shadow(18.dp, RoundedCornerShape(26.dp)),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(
                    Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Text(title, color = LoginInk, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = LoginMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    content()
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Canlı sohbet · Gerçek bağlar", color = LoginMuted.copy(.82f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    symbol: String,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = {
            Box(Modifier.size(32.dp).clip(CircleShape).background(LoginLavender), contentAlignment = Alignment.Center) {
                Text(symbol, color = LoginDeepPurple, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        },
        trailingIcon = if (isPassword) {
            { TextButton(onClick = onTogglePassword, contentPadding = PaddingValues(0.dp)) {
                Text(if (passwordVisible) "◉" else "○", color = LoginDeepPurple, fontWeight = FontWeight.Bold)
            } }
        } else null,
        singleLine = true,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().height(62.dp),
        shape = RoundedCornerShape(17.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = LoginInk,
            unfocusedTextColor = LoginInk,
            focusedContainerColor = LoginBackground,
            unfocusedContainerColor = LoginBackground,
            cursorColor = LoginPurple,
            focusedBorderColor = LoginPurple,
            unfocusedBorderColor = Color(0xFFE9DFE8),
            focusedLabelColor = LoginPurple,
            unfocusedLabelColor = LoginMuted,
        ),
    )
}

@Composable
private fun LoginPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(17.dp))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(LoginDeepPurple, LoginPurple, LoginPink))
                else Brush.horizontalGradient(listOf(LoginLavender, LoginSoftPink)),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else LoginMuted, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun RegisterScreen(onRegister: suspend (String, String, String) -> Result<Unit>, onAuthenticated: () -> Unit, onBack: () -> Unit) {
    LoginLayout(title = "Ses10'a katıl", subtitle = "Hesabını oluştur ve toplulukla sesini paylaş.") {
        var username by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var passwordVisible by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        LoginTextField(username, { username = it }, "Kullanıcı adı", "♙")
        LoginTextField(email, { email = it }, "E-posta", "✉")
        LoginTextField(password, { password = it }, "Şifre", "●", isPassword = true, passwordVisible = passwordVisible, onTogglePassword = { passwordVisible = !passwordVisible })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LoginPrimaryButton(
            text = if (busy) "Kaydediliyor..." else "Hesap oluştur",
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
            colors = ButtonDefaults.textButtonColors(contentColor = LoginPurple, disabledContentColor = LoginMuted.copy(alpha = 0.45f)),
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
