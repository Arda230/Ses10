package com.seson.app.feature.room

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.seson.app.core.livekit.RoomAudioSession
import kotlinx.coroutines.launch

private val RoomBackground = Color(0xFF060A16)
private val RoomSurface = Color(0xFF11182A)
private val NeonViolet = Color(0xFF9E78FF)
private val NeonBlue = Color(0xFF357CFF)
private val LiveCyan = Color(0xFF27D9F5)
private val PremiumGold = Color(0xFFFFD278)
private val MutedLabel = Color(0xFF8B95AD)

private data class MicSeat(val id: Int, val name: String? = null, val initials: String = "+", val muted: Boolean = true, val speaking: Boolean = false, val locked: Boolean = false)

@Composable
fun RoomScreen(roomName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(roomName) { RoomAudioSession.controller(context, roomName) }
    val roomSeats = remember { (1..12).map(::MicSeat) }
    val backendState by controller.roomState.collectAsState()
    val liveKitParticipants by controller.liveKitParticipants.collectAsState()
    val liveKitMicrophones by controller.liveKitMicrophones.collectAsState()
    val seats = roomSeats.map { roomSeat ->
        val occupant = backendState?.seats?.firstOrNull { it.id == roomSeat.id }?.occupant
        if (occupant == null) roomSeat else MicSeat(
            id = roomSeat.id,
            name = occupant.name,
            initials = occupant.name.initials(),
            muted = occupant.muted || (occupant.identity in liveKitParticipants && occupant.identity !in liveKitMicrophones),
            speaking = !occupant.muted && occupant.identity in liveKitMicrophones,
        )
    }
    val selectedSeatId = backendState?.selfSeatId
    var microphoneOn by remember(controller) { mutableStateOf(controller.isMicrophonePublished()) }
    var message by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var serviceReady by remember { mutableStateOf(false) }
    var connectionLabel by remember { mutableStateOf("Bağlanıyor...") }
    var showGiftPanel by remember { mutableStateOf(false) }

    fun changeMicrophone(enabled: Boolean) {
        if (selectedSeatId == null || !connected) return
        scope.launch {
            runCatching { controller.setMicrophoneEnabled(enabled) }
                .onSuccess { published ->
                    microphoneOn = published
                    connectionLabel = if (published) "Mikrofon yayında" else "Mikrofon kapalı"
                }
                .onFailure {
                    microphoneOn = controller.isMicrophonePublished()
                    connectionLabel = it.message ?: "Mikrofon yayınlanamadı"
                }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            RoomAudioSession.startForegroundService(context, roomName)
            changeMicrophone(true)
        } else connectionLabel = "Mikrofon izni gerekli"
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        RoomAudioSession.startForegroundService(context, roomName)
        serviceReady = true
        if (!granted) connectionLabel = "Bildirim izni kapalı; oda arka planda korunuyor"
    }

    LaunchedEffect(controller) {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else {
            RoomAudioSession.startForegroundService(context, roomName)
            serviceReady = true
        }
    }
    LaunchedEffect(controller, serviceReady) {
        if (serviceReady) {
            runCatching { RoomAudioSession.ensureConnected(context, roomName) }
                .onSuccess { connected = true; connectionLabel = (backendState?.participantCount ?: 1).toString() + " dinleyici" }
                .onFailure { connectionLabel = it.message ?: "Bağlantı kurulamadı" }
        }
    }

    Scaffold(
        containerColor = RoomBackground,
        topBar = {
            Row(Modifier.fillMaxWidth().background(Color(0xF2060A16)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { RoomAudioSession.leaveAndStop(context); onBack() } }) { Text("‹  Keşfet", color = Color(0xFFEAF2FF), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                Text(connectionLabel, color = Color(0xFFB9C5DA), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
                onGiftClick = { showGiftPanel = true },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(0f to Color(0xFF161238), .28f to Color(0xFF090E20), 1f to RoomBackground)).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoomHeader(roomName, backendState?.participantCount ?: 0)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SES10 SAHNESİ", color = LiveCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("12 konuşmacı koltuğu", color = Color(0xFF9CA7BD), fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("•  CANLI ODA", color = LiveCyan.copy(alpha = .9f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            }
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val compact = maxWidth < 360.dp
                val normalAvatarSize = if (compact) 44.dp else 50.dp
                val hostAvatarSize = if (compact) 57.dp else 64.dp
                val horizontalSpacing = if (compact) 2.dp else 7.dp
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SeatCard(
                            seat = seats.first(),
                            isOwnSeat = selectedSeatId == seats.first().id,
                            microphoneOn = microphoneOn,
                            modifier = Modifier.width(if (compact) 94.dp else 112.dp),
                            avatarSize = hostAvatarSize,
                            onClick = { handleSeatClick(seats.first(), selectedSeatId, connected, controller, scope, { microphoneOn = it }, { connectionLabel = it }) },
                        )
                        SeatCard(
                            seat = seats[11],
                            isOwnSeat = selectedSeatId == seats[11].id,
                            microphoneOn = microphoneOn,
                            modifier = Modifier.width(if (compact) 66.dp else 76.dp),
                            avatarSize = normalAvatarSize,
                            onClick = { handleSeatClick(seats[11], selectedSeatId, connected, controller, scope, { microphoneOn = it }, { connectionLabel = it }) },
                        )
                    }
                    Spacer(Modifier.height(if (compact) 18.dp else 24.dp))
                    StageSeatRow(seats.slice(1..5), spacing = horizontalSpacing) { seat ->
                        SeatCard(seat, selectedSeatId == seat.id, microphoneOn, Modifier.weight(1f), normalAvatarSize) {
                            handleSeatClick(seat, selectedSeatId, connected, controller, scope, { microphoneOn = it }, { connectionLabel = it })
                        }
                    }
                    Spacer(Modifier.height(if (compact) 25.dp else 34.dp))
                    StageSeatRow(seats.slice(6..10), spacing = horizontalSpacing) { seat ->
                        SeatCard(seat, selectedSeatId == seat.id, microphoneOn, Modifier.weight(1f), normalAvatarSize) {
                            handleSeatClick(seat, selectedSeatId, connected, controller, scope, { microphoneOn = it }, { connectionLabel = it })
                        }
                    }
                }
            }
            RoomChatPreview()
        }
    }
    if (showGiftPanel) GiftPanel(onDismiss = { showGiftPanel = false })
}

private fun handleSeatClick(
    seat: MicSeat,
    selectedSeatId: Int?,
    connected: Boolean,
    controller: com.seson.app.core.livekit.LiveKitRoomController,
    scope: kotlinx.coroutines.CoroutineScope,
    setMicrophone: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
) {
    if (!connected) return
    scope.launch {
        when {
            selectedSeatId == seat.id -> runCatching { controller.leaveSeat() }.onSuccess { setMicrophone(false) }.onFailure { setStatus(it.message ?: "Koltuktan ayrılamadı") }
            seat.name == null -> runCatching {
                if (selectedSeatId != null) controller.leaveSeat()
                controller.claimSeat(seat.id)
            }.onSuccess { setMicrophone(false) }.onFailure { setStatus(it.message ?: "Koltuğa çıkılamadı") }
        }
    }
}

private fun String.initials(): String = trim().split(Regex("\\s+")).take(2).joinToString("") { part ->
    part.firstOrNull()?.uppercase() ?: ""
}


@Composable
private fun RoomHeader(roomName: String, participantCount: Int) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xE811172A),
        border = BorderStroke(1.dp, Color(0xFF293657)),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(LiveCyan).shadow(8.dp, CircleShape, ambientColor = LiveCyan, spotColor = LiveCyan))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(roomName.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }, color = Color(0xFFF8F5FF), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("SES10  ·  ID $roomName", color = Color(0xFF929DB4), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = RoundedCornerShape(14.dp), color = LiveCyan.copy(alpha = .12f)) {
                Text("●  $participantCount çevrimiçi", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), color = LiveCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(7.dp))
            Text("•••", color = Color(0xFF9DA8BC), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StageSeatRow(
    seats: List<MicSeat>,
    spacing: androidx.compose.ui.unit.Dp,
    content: @Composable (MicSeat) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        seats.forEach { seat -> content(seat) }
    }
}

@Composable
private fun SeatCard(seat: MicSeat, isOwnSeat: Boolean, microphoneOn: Boolean, modifier: Modifier = Modifier, avatarSize: androidx.compose.ui.unit.Dp = 60.dp, onClick: () -> Unit) {
    val occupied = seat.name != null || isOwnSeat
    val isHostSeat = seat.id == 1
    val shownName = if (isOwnSeat) "Sen" else seat.name
    val shownInitials = if (isOwnSeat) "S10" else seat.initials
    val shownMuted = if (isOwnSeat) !microphoneOn else seat.muted
    val speaking = if (isOwnSeat) microphoneOn else seat.speaking
    val accent = when { isHostSeat -> PremiumGold; isOwnSeat -> LiveCyan; speaking -> LiveCyan; occupied -> NeonViolet; else -> Color(0xFF536078) }
    val transition = rememberInfiniteTransition(label = "speaker-${seat.id}")
    val pulse by transition.animateFloat(.95f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Column(
        modifier.clickable(enabled = isOwnSeat || (seat.name == null && !seat.locked), onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isHostSeat) Box(Modifier.size(avatarSize + 13.dp).shadow(18.dp, CircleShape, ambientColor = PremiumGold, spotColor = PremiumGold).border(1.dp, PremiumGold.copy(alpha = .32f), CircleShape))
            if (speaking) Box(Modifier.size(avatarSize + 11.dp).graphicsLayer { scaleX = pulse; scaleY = pulse; alpha = 1.38f - pulse }.border(2.dp, LiveCyan.copy(alpha = .9f), CircleShape))
            if (!occupied && !isHostSeat) {
                Box(
                    Modifier.size(avatarSize + 7.dp)
                        .shadow(9.dp, CircleShape, ambientColor = LiveCyan.copy(alpha = .35f), spotColor = NeonViolet.copy(alpha = .32f))
                        .border(1.dp, Brush.sweepGradient(listOf(LiveCyan.copy(alpha = .55f), NeonViolet.copy(alpha = .7f), Color.Transparent, LiveCyan.copy(alpha = .55f))), CircleShape),
                )
            }
            Box(
                Modifier.size(avatarSize)
                    .shadow(if (occupied) 15.dp else 4.dp, CircleShape, ambientColor = accent, spotColor = accent)
                    .clip(CircleShape)
                    .background(if (occupied) Brush.linearGradient(listOf(accent.copy(alpha = .95f), NeonBlue.copy(alpha = .62f), Color(0xFF171B34))) else Brush.radialGradient(listOf(Color(0xFF202B42), Color(0xFF101728))))
                    .border(if (isHostSeat) 2.dp else 1.dp, accent.copy(alpha = if (occupied) .92f else .5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!occupied && !isHostSeat && !seat.locked) {
                    EmptySeatMicrophone(Modifier.size(avatarSize * .52f))
                } else {
                    Text(if (seat.locked) "×" else shownInitials, color = if (occupied) Color.White else Color(0xFF9DA9BE), fontWeight = FontWeight.Black, fontSize = if (occupied) 14.sp else 24.sp)
                }
            }
            Surface(modifier = Modifier.align(Alignment.BottomEnd), shape = CircleShape, color = if (speaking) LiveCyan else Color(0xFF1A2235), border = BorderStroke(1.dp, RoomBackground)) {
                Text(if (speaking) "♫" else if (seat.locked) "⌁" else seat.id.toString(), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = if (speaking) Color(0xFF08221D) else Color(0xFFF1EBF6), fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            if (isHostSeat) Text("♛", Modifier.align(Alignment.TopCenter).graphicsLayer { translationY = -18f }.shadow(7.dp, CircleShape, ambientColor = PremiumGold, spotColor = PremiumGold), color = PremiumGold, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Text(shownName ?: if (isHostSeat) "Oda sahibi" else "Boş koltuk", color = if (occupied) Color(0xFFF8F4FC) else Color(0xFFAAB4C7), fontSize = 11.sp, fontWeight = if (isHostSeat || occupied) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(when { seat.locked -> "Kilitli"; !occupied -> "Mic ${seat.id}"; speaking -> "Konuşuyor"; shownMuted -> "Mic kapalı"; else -> "Mic açık" }, color = when { speaking -> LiveCyan; isHostSeat -> PremiumGold; else -> Color(0xFF7F8BA3) }, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun EmptySeatMicrophone(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.7.dp.toPx()
        val glow = LiveCyan.copy(alpha = .22f)
        val body = Color(0xFFB8D9EA)
        drawRoundRect(color = glow, topLeft = Offset(size.width * .25f, size.height * .05f), size = Size(size.width * .5f, size.height * .62f), cornerRadius = CornerRadius(size.width * .25f), style = Stroke(width = stroke * 3f))
        drawRoundRect(color = body, topLeft = Offset(size.width * .32f, size.height * .08f), size = Size(size.width * .36f, size.height * .52f), cornerRadius = CornerRadius(size.width * .18f), style = Stroke(width = stroke))
        drawArc(color = LiveCyan, startAngle = 0f, sweepAngle = 180f, useCenter = false, topLeft = Offset(size.width * .2f, size.height * .28f), size = Size(size.width * .6f, size.height * .48f), style = Stroke(width = stroke))
        drawLine(LiveCyan, Offset(size.width * .5f, size.height * .76f), Offset(size.width * .5f, size.height * .88f), stroke)
        drawLine(body, Offset(size.width * .34f, size.height * .88f), Offset(size.width * .66f, size.height * .88f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}

@Composable
private fun RoomChatPreview() {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xC40D1424), border = BorderStroke(1.dp, Color(0xFF202D48))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ODA SOHBETİ", color = LiveCyan, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("S10", color = NeonViolet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(7.dp))
                Text("Odaya hoş geldin · mesajlar burada akacak", color = Color(0xFFBEC8DA), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
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
    onGiftClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFA080D19)).imePadding().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = message, onValueChange = onMessageChange, modifier = Modifier.weight(1f).height(46.dp), placeholder = { Text("Odaya mesaj yaz...", color = MutedLabel, fontSize = 13.sp) }, singleLine = true, shape = RoundedCornerShape(22.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = RoomSurface, unfocusedContainerColor = RoomSurface, focusedBorderColor = LiveCyan, unfocusedBorderColor = Color(0xFF26334D)), trailingIcon = { IconButton(onClick = { onMessageChange("") }, enabled = message.isNotBlank()) { Text("↑", color = if (message.isNotBlank()) NeonViolet else MutedLabel) } })
            GiftButton(onGiftClick)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
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
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(16.dp)).background(if (active) LiveCyan else RoomSurface).border(1.dp, if (active) LiveCyan else Color(0xFF2B3955), RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) { Text(symbol, color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .35f) else if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .45f))
    }
}


@Composable
private fun GiftButton(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.size(46.dp).shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = PremiumGold, spotColor = PremiumGold).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Color(0xFF6C4B19), Color(0xFF30203D))))
                .border(1.dp, PremiumGold.copy(alpha = .7f), RoundedCornerShape(16.dp)).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Text("◇", color = PremiumGold, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        Text("Hediye", color = PremiumGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GiftPanel(onDismiss: () -> Unit) {
    var selectedGift by remember { mutableStateOf("Yıldız") }
    val gifts = listOf("✦" to "Yıldız", "◇" to "Kristal", "♛" to "Taç", "♫" to "Melodi", "☄" to "Roket", "S10" to "İmza")
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14111F), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column { Text("Hediye gönder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Ses10 hediyeleri için görsel altyapı", color = MutedLabel, fontSize = 11.sp) }
            gifts.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    row.forEach { (symbol, name) -> GiftOption(symbol, name, selectedGift == name, Modifier.weight(1f)) { selectedGift = name } }
                }
            }
            Surface(shape = RoundedCornerShape(17.dp), color = PremiumGold.copy(alpha = .14f), border = BorderStroke(1.dp, PremiumGold.copy(alpha = .4f))) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("$selectedGift seçildi", color = PremiumGold, fontWeight = FontWeight.Bold)
                    Text("Gönderim yakında", color = Color(0xFFC9C1D2), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun GiftOption(symbol: String, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), color = if (selected) Color(0xFF2D2340) else Color(0xFF1B1727), border = BorderStroke(1.dp, if (selected) PremiumGold else Color(0xFF302A40))) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(symbol, color = if (selected) PremiumGold else NeonViolet, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(name, color = Color(0xFFF4EFF9), fontSize = 10.sp, maxLines = 1)
        }
    }
}
