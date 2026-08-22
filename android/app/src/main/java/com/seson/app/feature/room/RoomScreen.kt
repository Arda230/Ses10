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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

private val RoomBackground = Color(0xFFFFF7FC)
private val RoomSoftPink = Color(0xFFFCEAF5)
private val RoomLavender = Color(0xFFEEE5FF)
private val RoomPurple = Color(0xFFA84FEA)
private val RoomDeepPurple = Color(0xFF7B4ED8)
private val RoomPink = Color(0xFFF062C0)
private val RoomInk = Color(0xFF29232E)
private val RoomMuted = Color(0xFF8D8492)
private val RoomSurface = Color.White
private val NeonViolet = RoomPurple
private val NeonBlue = RoomDeepPurple
private val LiveCyan = RoomPink
private val PremiumGold = Color(0xFFFFD278)
private val MutedLabel = RoomMuted

private enum class RoomThemeOption(
    val key: String,
    val title: String,
    val category: String,
    val background: Color,
    val middle: Color,
    val accent: Color,
    val dark: Boolean,
) {
    Ses10Purple("ses10_purple", "Ses10 Purple", "Galeri", Color(0xFFFFF7FC), Color(0xFFEEE5FF), Color(0xFFA84FEA), false),
    NeonNight("neon_night", "Neon Night", "Galeri", Color(0xFF21112F), Color(0xFF6C278E), Color(0xFFFF5BC1), true),
    Luxury("luxury", "Luxury", "Premium", Color(0xFFFFF8ED), Color(0xFFF1D6A5), Color(0xFF9A66D7), false),
    Romance("romance", "Romance", "Galeri", Color(0xFFFFF3F8), Color(0xFFF7C8DF), Color(0xFFF062C0), false),
    DarkClub("dark_club", "Dark Club", "Premium", Color(0xFF160D20), Color(0xFF4C1B62), Color(0xFFC351F0), true),
    Galaxy("galaxy", "Galaxy", "Premium", Color(0xFF17132E), Color(0xFF51418E), Color(0xFFED63C3), true);

    val foreground: Color get() = if (dark) Color(0xFFFFF7FF) else RoomInk
    val mutedForeground: Color get() = if (dark) Color(0xFFD5C3DB) else RoomMuted
    val glass: Color get() = if (dark) Color(0x661A1025) else Color.White.copy(.46f)
    val glassStrong: Color get() = if (dark) Color(0xA6211430) else Color.White.copy(.68f)

    companion object {
        fun fromKey(key: String?): RoomThemeOption = entries.firstOrNull { it.key == key } ?: Ses10Purple
    }
}

private data class MicSeat(val id: Int, val userId: String? = null, val identity: String? = null, val role: String = "listener", val name: String? = null, val initials: String = "+", val muted: Boolean = true, val speaking: Boolean = false, val locked: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(roomName: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember { context.getSharedPreferences("ses10_room_themes", android.content.Context.MODE_PRIVATE) }
    var appliedTheme by remember(roomName) { mutableStateOf(RoomThemeOption.fromKey(themePreferences.getString("theme_$roomName", null))) }
    var showThemePanel by remember { mutableStateOf(false) }
    var showRoomSettings by remember { mutableStateOf(false) }
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
        containerColor = appliedTheme.background,
        topBar = {
            Row(Modifier.fillMaxWidth().background(appliedTheme.background).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { RoomAudioSession.leaveAndStop(context); onBack() } }) { Text("‹  Çık", color = appliedTheme.foreground, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                Text(connectionLabel, color = appliedTheme.mutedForeground, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
                onMoreClick = { showRoomSettings = true },
                theme = appliedTheme,
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).background(Brush.verticalGradient(listOf(appliedTheme.background, appliedTheme.middle.copy(alpha = .78f), appliedTheme.accent.copy(alpha = .28f), appliedTheme.background))).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RoomHeader(roomName, backendState?.participantCount ?: 0, appliedTheme) { showRoomSettings = true }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("SES10 SAHNESİ", color = RoomPurple, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text("12 konuşmacı koltuğu", color = appliedTheme.mutedForeground, fontSize = 10.sp)
                }
                Spacer(Modifier.weight(1f))
                Text("●  CANLI ODA", color = RoomPink, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val avatarSize = if (maxWidth < 360.dp) 42.dp else 48.dp
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                    listOf(seats.slice(0..3), seats.slice(4..7), seats.slice(8..11)).forEach { row ->
                        StageSeatRow(row, 8.dp) { seat ->
                            SeatCard(seat, selectedSeatId == seat.id, microphoneOn, Modifier.weight(1f), avatarSize, appliedTheme, if (seat.id == 1) hostEntranceKey else 0) {
                                handleSeatClick(seat, selectedSeatId, connected, controller, scope, { microphoneOn = it }, { connectionLabel = it }) { if (seat.id == 1) hostEntranceKey += 1 }
                            }
                        }
                    }
                }
            }
            if ((backendState?.selfRole == "host" || backendState?.selfRole == "moderator") && backendState?.handRaises?.isNotEmpty() == true) TextButton(onClick = { showHandRequests = true }) { Text("✋ ${backendState?.handRaises?.size} konuşma isteği", color = PremiumGold) }
            TextButton(onClick = { showParticipants = true }) { Text("Katılımcılar (${backendState?.participantCount ?: 0})", color = LiveCyan) }
            RoomChatPreview(backendState?.messages.orEmpty(), appliedTheme)
        }
    }
    if (showRoomSettings) RoomSettingsPanel(
        canManage = backendState?.selfRole == "host" || backendState?.selfRole == "moderator",
        onDismiss = { showRoomSettings = false },
        onProfile = {
            val selfUserId = backendState?.participants?.firstOrNull { it.identity == backendState?.selfIdentity }?.userId
            if (selfUserId != null) scope.launch { selectedProfile = runCatching { controller.publicProfile(selfUserId) }.getOrNull() }
            showRoomSettings = false
        },
        onTheme = { showRoomSettings = false; showThemePanel = true },
    )
    if (showThemePanel) RoomThemePanel(appliedTheme, { showThemePanel = false }) { theme -> appliedTheme = theme; themePreferences.edit().putString("theme_$roomName", theme.key).apply(); showThemePanel = false }
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


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoomHeader(roomName: String, participantCount: Int, theme: RoomThemeOption, onSettingsClick: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    val displayName = roomName.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }
    val marqueeText = "$displayName   •   $roomName   •   $displayName"

    Surface(
        modifier = Modifier.fillMaxWidth().height(50.dp).shadow(3.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = theme.glass,
        border = BorderStroke(1.dp, theme.accent.copy(.24f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(RoomPurple, RoomPink))),
                contentAlignment = Alignment.Center,
            ) {
                Text("S10", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = marqueeText,
                modifier = Modifier.padding(start = 9.dp).weight(1f).basicMarquee(
                    iterations = Int.MAX_VALUE,
                    repeatDelayMillis = 1_100,
                    velocity = 24.dp,
                ),
                color = theme.foreground,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
            )
            Surface(color = theme.glassStrong, shape = RoundedCornerShape(12.dp)) {
                Text(
                    "● $participantCount",
                    Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = theme.accent,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(3.dp))
            Box {
                Text(
                    "•••",
                    Modifier.size(34.dp).clip(CircleShape).clickable { menuExpanded = true }.padding(top = 7.dp),
                    color = theme.foreground,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.width(188.dp).background(Color.Transparent),
                    shape = RoundedCornerShape(18.dp),
                    containerColor = theme.glassStrong,
                    tonalElevation = 0.dp,
                    shadowElevation = 7.dp,
                ) {
                    DropdownMenuItem(
                        text = { Text("Oda Ayarları", color = theme.foreground, fontWeight = FontWeight.Bold) },
                        leadingIcon = { Text("⚙", color = theme.accent, fontWeight = FontWeight.Bold) },
                        trailingIcon = { Text("›", color = RoomPink, fontSize = 20.sp) },
                        onClick = { menuExpanded = false; onSettingsClick() },
                    )
                }
            }
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
private fun SeatCard(seat: MicSeat, isOwnSeat: Boolean, microphoneOn: Boolean, modifier: Modifier = Modifier, avatarSize: androidx.compose.ui.unit.Dp = 60.dp, theme: RoomThemeOption, hostEntranceKey: Int = 0, onClick: () -> Unit) {
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
                    .background(if (occupied) Brush.linearGradient(listOf(accent.copy(alpha = .95f), NeonBlue.copy(alpha = .62f), RoomPink.copy(alpha = .48f))) else Brush.radialGradient(listOf(theme.middle.copy(alpha = .72f), theme.glassStrong)))
                    .border(if (isHostSeat) 2.dp else 1.dp, accent.copy(alpha = if (occupied) .92f else .5f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (!occupied && !isHostSeat && !seat.locked) {
                    EmptySeatMicrophone(Modifier.size(avatarSize * .52f))
                } else {
                    Text(if (seat.locked) "×" else shownInitials, color = if (occupied) Color.White else Color(0xFF9DA9BE), fontWeight = FontWeight.Black, fontSize = if (occupied) 14.sp else 24.sp)
                }
            }
            Surface(modifier = Modifier.align(Alignment.BottomEnd), shape = CircleShape, color = if (speaking) LiveCyan else Color.White, border = BorderStroke(1.dp, RoomBackground)) {
                Text(if (speaking) "♫" else if (seat.locked) "⌁" else seat.id.toString(), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = if (speaking) Color(0xFF08221D) else RoomDeepPurple, fontSize = 8.sp, fontWeight = FontWeight.Black)
            }
            if (isHostSeat) Text("♛", Modifier.align(Alignment.TopCenter).graphicsLayer { translationY = -18f + crownDrop.value * 24.dp.toPx(); alpha = crownAlpha.value; scaleX = .82f + crownAlpha.value * .18f; scaleY = scaleX }.shadow(7.dp, CircleShape, ambientColor = PremiumGold, spotColor = PremiumGold), color = PremiumGold, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
        Text(shownName ?: if (isHostSeat) "Oda sahibi" else "Boş koltuk", color = if (occupied) theme.foreground else theme.mutedForeground, fontSize = 11.sp, fontWeight = if (isHostSeat || occupied) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(when { seat.locked -> "Kilitli"; !occupied -> "Mic ${seat.id}"; speaking -> "Konuşuyor"; shownMuted -> "Mic kapalı"; else -> "Mic açık" }, color = when { speaking -> LiveCyan; isHostSeat -> PremiumGold; else -> theme.mutedForeground }, fontSize = 9.sp, maxLines = 1)
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
private fun RoomChatPreview(messages: List<com.seson.app.core.network.ApiMessage>, theme: RoomThemeOption) {
    Surface(shape = RoundedCornerShape(18.dp), color = theme.glass, border = BorderStroke(1.dp, theme.accent.copy(.22f))) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("ODA SOHBETİ", color = theme.accent, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            if (messages.isEmpty()) Text("Ses10 odasına hoş geldin ✨", color = theme.mutedForeground, fontSize = 10.sp)
            messages.takeLast(2).forEach { item -> Row { Text(if (item.type == "user") item.displayName else "Sistem", color = if (item.type == "user") RoomPurple else PremiumGold, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.width(7.dp)); Text(item.body, color = theme.foreground.copy(.82f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
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
    onMoreClick: () -> Unit,
    theme: RoomThemeOption,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 10.dp, vertical = 7.dp)
            .shadow(14.dp, RoundedCornerShape(27.dp), ambientColor = RoomPurple.copy(.18f), spotColor = RoomPink.copy(.14f)),
        color = theme.glass,
        shape = RoundedCornerShape(27.dp),
        border = BorderStroke(1.dp, theme.accent.copy(.22f)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = message,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f).height(46.dp),
                placeholder = { Text("Mesaj yaz...", color = theme.mutedForeground, fontSize = 11.sp, maxLines = 1) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.foreground,
                    unfocusedTextColor = theme.foreground,
                    cursorColor = theme.accent,
                    focusedContainerColor = theme.glassStrong,
                    unfocusedContainerColor = theme.glassStrong,
                    focusedBorderColor = RoomPurple,
                    unfocusedBorderColor = Color.Transparent,
                ),
                trailingIcon = {
                    IconButton(onClick = onSendMessage, enabled = message.isNotBlank(), modifier = Modifier.size(34.dp)) {
                        Text("↑", color = if (message.isNotBlank()) theme.accent else theme.mutedForeground, fontWeight = FontWeight.Black)
                    }
                },
            )
            DockButton("☺", active = false, enabled = false, size = 40.dp, theme = theme) {}
            DockButton("◇", active = true, size = 43.dp, gift = true, theme = theme, onClick = onGiftClick)
            DockButton("✋", active = handRaised, size = 42.dp, theme = theme, onClick = onHandRaise)
            DockButton(if (microphoneOn) "♫" else "⌁", active = microphoneOn, enabled = canUseMicrophone, size = 48.dp, microphone = true, theme = theme, onClick = onMicrophoneClick)
            DockButton("•••", active = false, size = 40.dp, theme = theme, onClick = onMoreClick)
        }
    }
}

@Composable
private fun DockButton(
    symbol: String,
    active: Boolean,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp,
    gift: Boolean = false,
    microphone: Boolean = false,
    theme: RoomThemeOption,
    onClick: () -> Unit,
) {
    val shape = CircleShape
    val glow = if (microphone && active) 12.dp else if (gift) 7.dp else 0.dp
    val background = when {
        microphone && active -> Brush.linearGradient(listOf(RoomDeepPurple, RoomPurple, RoomPink))
        gift -> Brush.linearGradient(listOf(RoomPink, RoomPurple))
        active -> Brush.linearGradient(listOf(RoomPurple, RoomPink))
        else -> Brush.linearGradient(listOf(theme.glassStrong, theme.middle.copy(alpha = .62f)))
    }
    Box(
        Modifier.size(size)
            .then(if (glow > 0.dp) Modifier.shadow(glow, shape, ambientColor = RoomPurple, spotColor = RoomPink) else Modifier)
            .clip(shape).background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            color = when {
                !enabled -> RoomMuted.copy(.48f)
                active || gift -> Color.White
                else -> theme.foreground
            },
            fontSize = if (symbol == "•••") 11.sp else 18.sp,
            fontWeight = FontWeight.Bold,
        )
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, contentColor = RoomInk) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSettingsPanel(
    canManage: Boolean,
    onDismiss: () -> Unit,
    onProfile: () -> Unit,
    onTheme: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = RoomBackground,
        contentColor = RoomInk,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Oda Ayarları", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(if (canManage) "Oda yönetimi ve görünüm" else "Oda bilgileri ve görünüm", color = RoomMuted)
            SettingsGroup {
                SettingsRow("♙", "Profil", "Odadaki profilini görüntüle", enabled = true, onClick = onProfile)
                SettingsRow("✎", "Odanın Adı", if (canManage) "Yakında" else "Yalnızca yöneticiler", enabled = false)
                SettingsRow("☷", "Duyuru", if (canManage) "Yakında" else "Yalnızca yöneticiler", enabled = false)
            }
            SettingsGroup {
                SettingsRow("✉", "Kimler Oda Sohbeti Gönderebilir", "Herkes · Yakında", enabled = false)
                SettingsRow("♬", "Kimler Mikrofona Çıkabilir", "İzin verilenler · Yakında", enabled = false)
                SettingsRow("◉", "Mikrofon Sayısı", "12 mikrofon", enabled = false)
                SettingsRow("⌁", "Oda Şifresi", if (canManage) "Kapalı · Yakında" else "Yalnızca yöneticiler", enabled = false)
                SettingsToggleRow("✦", "Süper Mikrofon", "Yakında", checked = false, enabled = false)
            }
            SettingsGroup {
                SettingsRow("✿", "Oda Teması", "Atmosferi ve arka planı değiştir", enabled = true, accent = true, onClick = onTheme)
                SettingsRow("♛", "Yöneticiler", if (canManage) "Yönetici araçları · Yakında" else "Yalnızca yöneticiler", enabled = false)
                SettingsRow("⊘", "Engellenenler Listesi", "Yakında", enabled = false)
                SettingsRow("↪", "Odadan Atma Geçmişi", "Yakında", enabled = false)
            }
        }
    }
}

@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White.copy(.94f),
        border = BorderStroke(1.dp, RoomLavender),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), content = content)
    }
}

@Composable
private fun SettingsRow(
    symbol: String,
    title: String,
    detail: String,
    enabled: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape)
                .background(if (accent) Brush.linearGradient(listOf(RoomPurple, RoomPink)) else Brush.linearGradient(listOf(RoomLavender, RoomSoftPink))),
            contentAlignment = Alignment.Center,
        ) { Text(symbol, color = if (accent) Color.White else RoomDeepPurple, fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, color = if (enabled) RoomInk else RoomInk.copy(.58f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = RoomMuted.copy(if (enabled) 1f else .65f), fontSize = 10.sp, maxLines = 1)
        }
        Text(if (enabled) "›" else "—", color = if (enabled) RoomPurple else RoomMuted.copy(.45f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsToggleRow(symbol: String, title: String, detail: String, checked: Boolean, enabled: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(RoomLavender), contentAlignment = Alignment.Center) { Text(symbol, color = RoomDeepPurple, fontWeight = FontWeight.Bold) }
        Column(Modifier.padding(start = 11.dp).weight(1f)) {
            Text(title, color = RoomInk.copy(if (enabled) 1f else .58f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(detail, color = RoomMuted, fontSize = 10.sp)
        }
        Box(
            Modifier.size(42.dp, 24.dp).clip(CircleShape).background(if (checked) RoomPurple else RoomLavender).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) { Box(Modifier.size(18.dp).clip(CircleShape).background(if (enabled) Color.White else RoomMuted.copy(.5f))) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomThemePanel(
    applied: RoomThemeOption,
    onDismiss: () -> Unit,
    onApply: (RoomThemeOption) -> Unit,
) {
    var tab by remember { mutableStateOf("Galeri") }
    var selected by remember(applied) { mutableStateOf(applied) }
    val visibleThemes = when (tab) {
        "Premium" -> RoomThemeOption.entries.filter { it.category == "Premium" }
        "Özelleştir" -> RoomThemeOption.entries.take(2)
        else -> RoomThemeOption.entries.filter { it.category == "Galeri" }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White, contentColor = RoomInk) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Oda Teması", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text("Odanın atmosferini seç", color = RoomMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Galeri", "Premium", "Özelleştir").forEach { item ->
                    Surface(
                        modifier = Modifier.weight(1f).clickable { tab = item },
                        shape = RoundedCornerShape(15.dp),
                        color = if (tab == item) RoomLavender else RoomSoftPink,
                    ) { Text(item, Modifier.padding(vertical = 9.dp), color = if (tab == item) RoomPurple else RoomMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleThemes, key = { it.key }) { theme ->
                    ThemePreviewCard(theme, selected == theme) { selected = theme }
                }
            }
            if (tab == "Özelleştir") Text("Renk ve dekorasyon seçenekleri yakında genişleyecek.", color = RoomMuted, fontSize = 11.sp)
            Box(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
                    .background(Brush.horizontalGradient(listOf(RoomPurple, RoomPink)))
                    .clickable { onApply(selected) },
                contentAlignment = Alignment.Center,
            ) { Text("Temayı Uygula", color = Color.White, fontWeight = FontWeight.ExtraBold) }
        }
    }
}

@Composable
private fun ThemePreviewCard(theme: RoomThemeOption, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.width(152.dp).height(178.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(21.dp),
        color = Color.White,
        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) RoomPurple else RoomLavender),
        shadowElevation = if (selected) 8.dp else 2.dp,
    ) {
        Column {
            Box(
                Modifier.fillMaxWidth().height(120.dp)
                    .background(Brush.linearGradient(listOf(theme.background, theme.middle, theme.accent))),
            ) {
                repeat(4) { index ->
                    Box(
                        Modifier.size((24 + index * 3).dp).align(if (index < 2) Alignment.CenterStart else Alignment.CenterEnd)
                            .padding(if (index % 2 == 0) 4.dp else 0.dp).clip(CircleShape)
                            .background(Color.White.copy(.42f)),
                    )
                }
                if (selected) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(25.dp).clip(CircleShape).background(RoomPurple), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontWeight = FontWeight.Black) }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(theme.title, color = RoomInk, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(theme.category, color = RoomMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun GiftOption(symbol: String, name: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), color = if (selected) RoomLavender else RoomSoftPink, border = BorderStroke(1.dp, if (selected) RoomPurple else RoomLavender)) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(symbol, color = if (selected) PremiumGold else NeonViolet, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(name, color = RoomInk, fontSize = 10.sp, maxLines = 1)
        }
    }
}
