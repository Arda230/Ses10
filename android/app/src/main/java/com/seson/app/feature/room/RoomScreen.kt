package com.seson.app.feature.room

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.pow

private val RoomBackground = Color(0xFF060A16)
private val RoomSurface = Color(0xFF11182A)
private val NeonViolet = Color(0xFF9E78FF)
private val NeonBlue = Color(0xFF357CFF)
private val LiveCyan = Color(0xFF27D9F5)
private val PremiumGold = Color(0xFFFFD278)
private val MutedLabel = Color(0xFF8B95AD)

private data class MicSeat(val id: Int, val userId: String? = null, val identity: String? = null, val role: String = "listener", val name: String? = null, val initials: String = "+", val muted: Boolean = true, val speaking: Boolean = false, val locked: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(roomName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(roomName) { RoomAudioSession.controller(context, roomName) }
    val roomSeats = remember { (1..12).map(::MicSeat) }
    val backendState by controller.roomState.collectAsState()
    val terminalMessage by controller.terminalMessage.collectAsState()
    val liveKitParticipants by controller.liveKitParticipants.collectAsState()
    val liveKitMicrophones by controller.liveKitMicrophones.collectAsState()
    val seats = roomSeats.map { roomSeat ->
        val backendSeat = backendState?.seats?.firstOrNull { it.id == roomSeat.id }
        val occupant = backendSeat?.occupant
        if (occupant == null) roomSeat.copy(locked = backendSeat?.locked ?: false) else MicSeat(
            id = roomSeat.id,
            userId = backendState?.participants?.firstOrNull { it.identity == occupant.identity }?.userId,
            identity = occupant.identity,
            role = backendState?.participants?.firstOrNull { it.identity == occupant.identity }?.role ?: "listener",
            name = occupant.name,
            initials = occupant.name.initials(),
            muted = occupant.muted || (occupant.identity in liveKitParticipants && occupant.identity !in liveKitMicrophones),
            speaking = !occupant.muted && occupant.identity in liveKitMicrophones,
            locked = backendSeat.locked,
        )
    }
    val selectedSeatId = backendState?.selfSeatId
    var microphoneOn by remember(controller) { mutableStateOf(controller.isMicrophonePublished()) }
    var message by remember { mutableStateOf("") }
    var connected by remember { mutableStateOf(false) }
    var serviceReady by remember { mutableStateOf(false) }
    var connectionLabel by remember { mutableStateOf("Bağlanıyor...") }
    var showGiftPanel by remember { mutableStateOf(false) }
    var giftCatalog by remember { mutableStateOf(emptyList<com.seson.app.core.network.GiftInfo>()) }
    var giftBalance by remember { mutableStateOf(0) }
    var hostEntranceKey by remember(controller) { mutableStateOf(0) }
    var handRaised by remember { mutableStateOf(false) }
    var showHandRequests by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var selectedProfile by remember { mutableStateOf<com.seson.app.core.network.ApiUser?>(null) }

    LaunchedEffect(terminalMessage) { if (terminalMessage != null) { connectionLabel = terminalMessage.orEmpty(); delay(900); RoomAudioSession.leaveAndStop(context); onBack() } }
    LaunchedEffect(backendState?.closed) { if (backendState?.closed == true) { connectionLabel = "Oda kapatıldı"; delay(900); RoomAudioSession.leaveAndStop(context); onBack() } }

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
                onSendMessage = { val text = message.trim(); if (text.isNotEmpty()) scope.launch { runCatching { controller.sendMessage(text) }.onSuccess { message = "" }.onFailure { connectionLabel = it.message ?: "Mesaj gönderilemedi" } } },
                microphoneOn = microphoneOn,
                canUseMicrophone = selectedSeatId != null && connected,
                onMicrophoneClick = {
                    if (microphoneOn) changeMicrophone(false)
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) changeMicrophone(true)
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                handRaised = handRaised,
                onHandRaise = { scope.launch { runCatching { controller.raiseHand(!handRaised) }.onSuccess { handRaised = !handRaised }.onFailure { connectionLabel = it.message ?: "El kaldırma güncellenemedi" } } },
                onGiftClick = { scope.launch { controller.gifts().let { giftCatalog = it.first; giftBalance = it.second; showGiftPanel = true } } },
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
                            hostEntranceKey = hostEntranceKey,
                            onClick = { handleSeatClick(seats.first(), selectedSeatId, connected, controller, scope, { microphoneOn = it }, { connectionLabel = it }) { hostEntranceKey += 1 } },
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
            if ((backendState?.selfRole == "host" || backendState?.selfRole == "moderator") && backendState?.handRaises?.isNotEmpty() == true) TextButton(onClick = { showHandRequests = true }) { Text("✋ ${backendState?.handRaises?.size} konuşma isteği", color = PremiumGold) }
            TextButton(onClick = { showParticipants = true }) { Text("Katılımcılar (${backendState?.participantCount ?: 0})", color = LiveCyan) }
            RoomChatPreview(backendState?.messages.orEmpty())
        }
    }
    if (showGiftPanel) GiftPanel(giftCatalog, backendState?.participants.orEmpty().filter { it.identity != backendState?.selfIdentity }, giftBalance, onDismiss = { showGiftPanel = false }, onSend = { receiver, gift -> scope.launch { runCatching { controller.sendGift(receiver, gift, java.util.UUID.randomUUID().toString()) }.onSuccess { giftBalance = it; showGiftPanel = false }.onFailure { connectionLabel = it.message ?: "Hediye gönderilemedi" } } })
    if (showHandRequests) ModalBottomSheet(onDismissRequest = { showHandRequests = false }, containerColor = RoomSurface) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Konuşma istekleri", fontWeight = FontWeight.Bold); backendState?.handRaises?.forEach { request -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(request.name); Row { TextButton(onClick = { scope.launch { controller.resolveHand(request.identity, false) } }) { Text("Reddet") }; TextButton(onClick = { scope.launch { controller.resolveHand(request.identity, true) } }) { Text("Kabul") } } } } } }
    if (showParticipants) ModalBottomSheet(onDismissRequest = { showParticipants = false }, containerColor = RoomSurface) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Katılımcılar ve yönetim", fontWeight = FontWeight.Bold); backendState?.participants?.forEach { person -> Column(Modifier.fillMaxWidth().clickable { scope.launch { selectedProfile = runCatching { controller.publicProfile(person.userId) }.getOrNull() } }.padding(vertical = 6.dp)) { Text("${person.name} · ${person.role}"); Text(person.userId, color = MutedLabel, fontSize = 9.sp); if ((backendState?.selfRole == "host" || backendState?.selfRole == "moderator") && person.identity != backendState?.selfIdentity && person.role != "host") Row { TextButton(onClick = { scope.launch { controller.muteParticipant(person.identity) } }) { Text("Mute") }; if (person.seatId != null) TextButton(onClick = { scope.launch { controller.removeFromSeat(person.identity) } }) { Text("İndir") }; TextButton(onClick = { scope.launch { controller.kick(person.identity) } }) { Text("Çıkar") }; if (backendState?.selfRole == "host") TextButton(onClick = { scope.launch { controller.setRole(person.userId, if (person.role == "moderator") "listener" else "moderator") } }) { Text(if (person.role == "moderator") "Mod kaldır" else "Mod yap") } } } }; if (backendState?.selfRole == "host" || backendState?.selfRole == "moderator") { Text("Koltuk kilitleri", color = PremiumGold); LazyRow { items(backendState?.seats.orEmpty(), key = { it.id }) { seat -> TextButton(onClick = { scope.launch { controller.setSeatLock(seat.id, !seat.locked) } }) { Text("${seat.id}: ${if (seat.locked) "Aç" else "Kilitle"}") } } } }; if (backendState?.selfRole == "host") TextButton(onClick = { scope.launch { controller.closeRoom() } }) { Text("Odayı kapat", color = Color.Red) } } }
    selectedProfile?.let { profile -> ModalBottomSheet(onDismissRequest = { selectedProfile = null }, containerColor = RoomSurface) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(profile.displayName, style = MaterialTheme.typography.titleLarge); Text("@${profile.username}"); Text("ID ${profile.id}", color = MutedLabel); Text("Rol: ${profile.role}") } } }
}

private fun handleSeatClick(
    seat: MicSeat,
    selectedSeatId: Int?,
    connected: Boolean,
    controller: com.seson.app.core.livekit.LiveKitRoomController,
    scope: kotlinx.coroutines.CoroutineScope,
    setMicrophone: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    onSeatClaimed: (Int) -> Unit = {},
) {
    if (!connected) return
    scope.launch {
        when {
            selectedSeatId == seat.id -> runCatching { controller.leaveSeat() }.onSuccess { setMicrophone(false) }.onFailure { setStatus(it.message ?: "Koltuktan ayrılamadı") }
            seat.name == null -> runCatching {
                if (selectedSeatId != null) controller.leaveSeat()
                controller.claimSeat(seat.id)
            }.onSuccess { setMicrophone(false); onSeatClaimed(seat.id) }.onFailure { setStatus(it.message ?: "Koltuğa çıkılamadı") }
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
private fun SeatCard(seat: MicSeat, isOwnSeat: Boolean, microphoneOn: Boolean, modifier: Modifier = Modifier, avatarSize: androidx.compose.ui.unit.Dp = 60.dp, hostEntranceKey: Int = 0, onClick: () -> Unit) {
    val occupied = seat.name != null || isOwnSeat
    val isHostSeat = seat.id == 1
    val shownName = if (isOwnSeat) "Sen" else seat.name
    val shownInitials = if (isOwnSeat) "S10" else seat.initials
    val shownMuted = if (isOwnSeat) !microphoneOn else seat.muted
    val speaking = if (isOwnSeat) microphoneOn else seat.speaking
    val accent = when { isHostSeat -> PremiumGold; isOwnSeat -> LiveCyan; speaking -> LiveCyan; occupied -> NeonViolet; else -> Color(0xFF536078) }
    val transition = rememberInfiniteTransition(label = "speaker-${seat.id}")
    val pulse by transition.animateFloat(.95f, 1.1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val entranceProgress = remember { Animatable(1f) }
    val crownDrop = remember { Animatable(0f) }
    val crownAlpha = remember { Animatable(1f) }
    LaunchedEffect(hostEntranceKey) {
        if (isHostSeat && isOwnSeat && hostEntranceKey > 0) {
            entranceProgress.snapTo(0f)
            crownDrop.snapTo(-1f)
            crownAlpha.snapTo(0f)
            coroutineScope {
                launch { entranceProgress.animateTo(1f, tween(2_400, easing = FastOutSlowInEasing)) }
                launch {
                    crownDrop.animateTo(
                        0f,
                        keyframes {
                            durationMillis = 1_200
                            -.07f at 980 using FastOutSlowInEasing
                            .085f at 1_075
                            -.03f at 1_145
                            0f at 1_200
                        },
                    )
                }
                launch { crownAlpha.animateTo(1f, tween(460, easing = FastOutSlowInEasing)) }
            }
        }
    }
    Column(
        modifier.clickable(enabled = isOwnSeat || (seat.name == null && !seat.locked), onClick = onClick).padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isHostSeat && isOwnSeat && entranceProgress.value < 1f) {
                HostEntranceGlow(avatarSize, entranceProgress.value, crownDrop.value, crownAlpha.value)
            }
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
            if (isHostSeat) Text("♛", Modifier.align(Alignment.TopCenter).graphicsLayer { translationY = -18f + crownDrop.value * 24.dp.toPx(); alpha = crownAlpha.value; scaleX = .82f + crownAlpha.value * .18f; scaleY = scaleX }.shadow(7.dp, CircleShape, ambientColor = PremiumGold, spotColor = PremiumGold), color = PremiumGold, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Text(shownName ?: if (isHostSeat) "Oda sahibi" else "Boş koltuk", color = if (occupied) Color(0xFFF8F4FC) else Color(0xFFAAB4C7), fontSize = 11.sp, fontWeight = if (isHostSeat || occupied) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(when { seat.locked -> "Kilitli"; !occupied -> "Mic ${seat.id}"; speaking -> "Konuşuyor"; shownMuted -> "Mic kapalı"; else -> "Mic açık" }, color = when { speaking -> LiveCyan; isHostSeat -> PremiumGold; else -> Color(0xFF7F8BA3) }, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun HostEntranceGlow(avatarSize: androidx.compose.ui.unit.Dp, progress: Float, crownDrop: Float, crownAlpha: Float) {
    Canvas(Modifier.size(avatarSize + 116.dp)) {
        val avatarRadius = avatarSize.toPx() / 2f
        val outerFade = 1f - ((progress - .14f) / .86f).coerceIn(0f, 1f)
        val landingFlash = (1f - abs(progress - .5f) / .075f).coerceIn(0f, 1f)
        val outerExpansion = 10.dp.toPx() + 41.dp.toPx() * progress
        val innerLife = (1f - progress / .8f).coerceIn(0f, 1f)
        val innerPulse = sin(progress * 42f) * 3.2.dp.toPx() * innerLife

        val crownY = center.y - avatarRadius - 17.dp.toPx() + crownDrop * 24.dp.toPx()
        val trailLife = crownAlpha * (1f - progress / .58f).coerceIn(0f, 1f)
        if (trailLife > 0f) {
            drawLine(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, PremiumGold.copy(alpha = trailLife * .16f), PremiumGold.copy(alpha = trailLife * .7f)),
                    startY = crownY - 37.dp.toPx(), endY = crownY,
                ),
                start = Offset(center.x, crownY - 37.dp.toPx()), end = Offset(center.x, crownY),
                strokeWidth = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawCircle(PremiumGold.copy(alpha = trailLife * .24f), 9.dp.toPx(), Offset(center.x, crownY))
        }

        drawCircle(PremiumGold.copy(alpha = landingFlash * .18f), avatarRadius + 17.dp.toPx() + landingFlash * 11.dp.toPx())
        drawCircle(
            color = PremiumGold.copy(alpha = outerFade * .94f), radius = avatarRadius + outerExpansion,
            style = Stroke(width = (4.4f - progress * 2.8f).dp.toPx()),
        )
        drawCircle(
            color = LiveCyan.copy(alpha = outerFade * .62f), radius = avatarRadius + outerExpansion + 4.dp.toPx(),
            style = Stroke(width = 1.6.dp.toPx()),
        )
        drawCircle(
            color = LiveCyan.copy(alpha = innerLife * .88f), radius = avatarRadius + 9.dp.toPx() + innerPulse,
            style = Stroke(width = (1.8f + innerLife * 1.3f).dp.toPx()),
        )
        drawCircle(
            color = PremiumGold.copy(alpha = landingFlash), radius = avatarRadius + 7.dp.toPx() + landingFlash * 15.dp.toPx(),
            style = Stroke(width = 2.4.dp.toPx()),
        )

        val directions = listOf(-1f, -.9f, -.79f, -.67f, -.54f, -.4f, -.25f, -.1f, .08f, .22f, .37f, .51f, .64f, .76f, .87f, .98f, -.33f, .31f)
        directions.forEachIndexed { index, direction ->
            val delay = (index % 7) * .025f
            val speedCurve = .78f + (index % 5) * .085f
            val particleProgress = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f).pow(speedCurve)
            val staggeredFade = (1f - particleProgress * particleProgress).coerceIn(0f, 1f)
            val sideDrift = direction * (avatarRadius + 21.dp.toPx()) * (.66f + particleProgress * .86f)
            val floatOffset = sin(particleProgress * (5.5f + index % 3) + index) * 5.dp.toPx()
            val downwardDrift = -19.dp.toPx() * particleProgress + (35 + index % 4 * 4).dp.toPx() * particleProgress * particleProgress
            val startY = center.y - avatarRadius * (.12f + (index % 5) * .15f)
            val particleCenter = Offset(center.x + sideDrift + floatOffset, startY + downwardDrift)
            val trailStart = Offset(
                particleCenter.x - direction * (6.dp.toPx() + particleProgress * 10.dp.toPx()),
                particleCenter.y - (6 + index % 3 * 2).dp.toPx(),
            )
            drawLine(
                color = PremiumGold.copy(alpha = staggeredFade * .38f), start = trailStart, end = particleCenter,
                strokeWidth = 1.1.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            drawCircle(
                color = PremiumGold.copy(alpha = staggeredFade * .9f),
                radius = (2.55f - particleProgress * 1.25f).dp.toPx(), center = particleCenter,
            )
            if (index % 3 == 0 || index % 7 == 0) {
                val starRadius = (4.2f - particleProgress * 1.8f).dp.toPx()
                drawLine(PremiumGold.copy(alpha = staggeredFade * .86f), Offset(particleCenter.x - starRadius, particleCenter.y), Offset(particleCenter.x + starRadius, particleCenter.y), .9.dp.toPx())
                drawLine(PremiumGold.copy(alpha = staggeredFade * .86f), Offset(particleCenter.x, particleCenter.y - starRadius), Offset(particleCenter.x, particleCenter.y + starRadius), .9.dp.toPx())
            }
        }

        val crownBurst = landingFlash * landingFlash
        listOf(-1f, -.72f, -.42f, 0f, .42f, .72f, 1f).forEachIndexed { index, direction ->
            val burstCenter = Offset(
                center.x + direction * (10.dp.toPx() + crownBurst * 19.dp.toPx()),
                center.y - avatarRadius - 13.dp.toPx() - crownBurst * (5 + index % 2 * 5).dp.toPx(),
            )
            drawCircle(PremiumGold.copy(alpha = crownBurst * .95f), (1.3f + crownBurst * 1.2f).dp.toPx(), burstCenter)
        }
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
private fun RoomChatPreview(messages: List<com.seson.app.core.network.ApiMessage>) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xC40D1424), border = BorderStroke(1.dp, Color(0xFF202D48))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ODA SOHBETİ", color = LiveCyan, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            if (messages.isEmpty()) Text("Henüz mesaj yok", color = Color(0xFFBEC8DA), fontSize = 10.sp)
            messages.takeLast(3).forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { Text(if (item.type == "user") item.displayName else "Sistem", color = if (item.type == "user") NeonViolet else PremiumGold, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text(item.body, color = Color(0xFFBEC8DA), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
        }
    }
}

@Composable
private fun RoomControls(
    message: String,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    handRaised: Boolean,
    onHandRaise: () -> Unit,
    microphoneOn: Boolean,
    canUseMicrophone: Boolean,
    onMicrophoneClick: () -> Unit,
    onGiftClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color(0xFA080D19)).imePadding().padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = message, onValueChange = onMessageChange, modifier = Modifier.weight(1f).height(46.dp), placeholder = { Text("Odaya mesaj yaz...", color = MutedLabel, fontSize = 13.sp) }, singleLine = true, shape = RoundedCornerShape(22.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = RoomSurface, unfocusedContainerColor = RoomSurface, focusedBorderColor = LiveCyan, unfocusedBorderColor = Color(0xFF26334D)), trailingIcon = { IconButton(onClick = onSendMessage, enabled = message.isNotBlank()) { Text("↑", color = if (message.isNotBlank()) NeonViolet else MutedLabel) } })
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
            ControlButton("✋", if (handRaised) "Geri çek" else "El kaldır", handRaised, onClick = onHandRaise)
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
private fun GiftPanel(
    gifts: List<com.seson.app.core.network.GiftInfo>,
    participants: List<com.seson.app.core.network.ApiParticipant>,
    balance: Int,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit,
) {
    var selectedGift by remember(gifts) { mutableStateOf(gifts.firstOrNull()?.id) }
    var selectedReceiver by remember(participants) { mutableStateOf(participants.firstOrNull()?.userId) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color(0xFF14111F), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hediye gönder · Bakiye $balance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Hediye", color = MutedLabel)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(gifts, key = { it.id }) { gift -> GiftOption(gift.assetIdentifier.take(2).uppercase(), "${gift.name} · ${gift.price}", selectedGift == gift.id, Modifier.width(105.dp)) { selectedGift = gift.id } }
            }
            Text("Alıcı", color = MutedLabel)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(participants, key = { it.userId }) { person -> Surface(Modifier.clickable { selectedReceiver = person.userId }, shape = RoundedCornerShape(14.dp), color = if (selectedReceiver == person.userId) PremiumGold.copy(alpha = .25f) else RoomSurface) { Text(person.name, Modifier.padding(12.dp)) } }
            }
            TextButton(onClick = { val receiver = selectedReceiver; val gift = selectedGift; if (receiver != null && gift != null) onSend(receiver, gift) }, enabled = selectedReceiver != null && selectedGift != null) { Text("Gönder", color = PremiumGold, fontWeight = FontWeight.Bold) }
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
