package com.seson.app.feature.room

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.seson.app.core.livekit.LiveKitRoomController
import kotlinx.coroutines.launch

private data class MicSeat(val id: Int, val name: String? = null, val initials: String = "+", val muted: Boolean = true)

@Composable
fun RoomScreen(roomName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(roomName) { LiveKitRoomController(context, roomName) }
    val seats = remember {
        listOf(
            MicSeat(1, "Mert", "ME", false), MicSeat(2, "Ece", "EC", false), MicSeat(3, "Lara", "LD"),
            MicSeat(4), MicSeat(5), MicSeat(6, "Deniz", "DA"), MicSeat(7), MicSeat(8), MicSeat(9), MicSeat(10), MicSeat(11), MicSeat(12),
        )
    }
    var selectedSeatId by remember { mutableStateOf<Int?>(null) }
    var microphoneOn by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var connectionLabel by remember { mutableStateOf("Bağlanıyor...") }

    fun changeMicrophone(enabled: Boolean) {
        if (selectedSeatId == null || !connected) return
        scope.launch {
            runCatching { controller.setMicrophoneEnabled(enabled) }
                .onSuccess { microphoneOn = enabled }
                .onFailure { connectionLabel = "Mic kullanılamadı" }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) changeMicrophone(true) else connectionLabel = "Mikrofon izni gerekli"
    }

    LaunchedEffect(controller) {
        runCatching { controller.connect() }
            .onSuccess { connected = true; connectionLabel = "128 dinleyici" }
            .onFailure { connectionLabel = it.message ?: "Bağlantı kurulamadı" }
    }
    DisposableEffect(controller) { onDispose { controller.close() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { controller.leaveAndDisconnect(); onBack() } }) { Text("‹ Keşfet") }
                Spacer(Modifier.weight(1f))
                Text(connectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        },
        bottomBar = {
            RoomControls(
                message = message,
                onMessageChange = { message = it },
                microphoneOn = microphoneOn,
                canUseMicrophone = selectedSeatId != null && connected,
                onMicrophoneClick = {
                    if (microphoneOn) changeMicrophone(false)
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) changeMicrophone(true)
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RoomHeader()
            Text("Konuşmacı sahnesi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(seats) { seat ->
                    SeatCard(
                        seat = seat,
                        isOwnSeat = selectedSeatId == seat.id,
                        microphoneOn = microphoneOn,
                        onClick = {
                            if (!connected) return@SeatCard
                            scope.launch {
                                when {
                                    selectedSeatId == seat.id -> runCatching { controller.leaveSeat() }.onSuccess {
                                        selectedSeatId = null
                                        microphoneOn = false
                                    }
                                    seat.name == null -> runCatching {
                                        if (selectedSeatId != null) controller.leaveSeat()
                                        controller.claimSeat(seat.id)
                                    }.onSuccess {
                                        selectedSeatId = seat.id
                                        microphoneOn = false
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomHeader() {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF171421))) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0x553E65E8), Color(0x335F35B5), Color.Transparent))).padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF6FE7D4))); Spacer(Modifier.width(7.dp)); Text("CANLI · MÜZİK & SOHBET", color = Color(0xFF6FE7D4), style = MaterialTheme.typography.labelMedium) }
                Text("Geceye Bir Şarkı", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Sesini paylaş, müziğe ve sohbete katıl.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SeatCard(seat: MicSeat, isOwnSeat: Boolean, microphoneOn: Boolean, onClick: () -> Unit) {
    val occupied = seat.name != null || isOwnSeat
    val shownName = if (isOwnSeat) "Sen" else seat.name
    val shownInitials = if (isOwnSeat) "S10" else seat.initials
    val shownMuted = if (isOwnSeat) !microphoneOn else seat.muted
    val accent = when {
        isOwnSeat -> Color(0xFF6FE7D4)
        occupied -> Color(0xFF9B7BFF)
        else -> Color(0xFF4C4658)
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = isOwnSeat || seat.name == null, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOwnSeat -> Color(0xFF17302F)
                occupied -> Color(0xFF1D1928)
                else -> Color(0xFF14121A)
            },
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 13.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(
                Modifier.size(48.dp).shadow(if (occupied) 10.dp else 0.dp, CircleShape, ambientColor = accent, spotColor = accent).clip(CircleShape).background(
                    if (occupied) Brush.linearGradient(listOf(accent, accent.copy(alpha = .55f)))
                    else Brush.linearGradient(listOf(Color(0xFF292532), Color(0xFF1D1A24))),
                ),
                contentAlignment = Alignment.Center,
            ) { Text(shownInitials, fontWeight = FontWeight.Bold, color = if (occupied) Color.White else Color(0xFF91899F)) }
            Text(shownName ?: "Boş koltuk", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(
                if (!occupied) "Katıl" else if (shownMuted) "Mic kapalı" else "Konuşuyor",
                color = if (occupied && !shownMuted) Color(0xFF6FE7D4) else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RoomControls(
    message: String,
    onMessageChange: (String) -> Unit,
    microphoneOn: Boolean,
    canUseMicrophone: Boolean,
    onMicrophoneClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFF121019)).imePadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = message, onValueChange = onMessageChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Odaya mesaj yaz...") }, singleLine = true, shape = RoundedCornerShape(18.dp), trailingIcon = { IconButton(onClick = { onMessageChange("") }, enabled = message.isNotBlank()) { Text("↑") } })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            ControlButton("🎁", "Hediye", false, onClick = {})
            ControlButton(
                if (microphoneOn) "♫" else "⌁",
                if (microphoneOn) "Mic açık" else "Mic kapalı",
                microphoneOn,
                onMicrophoneClick,
                enabled = canUseMicrophone,
            )
            ControlButton("✋", "El kaldır", false, onClick = {})
        }
    }
}

@Composable
private fun ControlButton(symbol: String, label: String, active: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Text(symbol, color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .35f) else if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .45f))
    }
}
