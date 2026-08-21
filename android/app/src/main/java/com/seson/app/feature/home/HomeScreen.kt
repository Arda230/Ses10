package com.seson.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seson.app.core.network.Ses10Api
import kotlinx.coroutines.launch

private enum class HomeTab(val label: String, val symbol: String) {
    Discover("Keşfet", "◉"), Messages("Mesajlar", "✦"), Profile("Profil", "●"),
}

private data class DiscoverRoom(
    val slug: String,
    val title: String,
    val category: String,
    val participants: Int,
    val hosts: String,
    val accent: Color,
)

private val PageBackground = Color(0xFF080711)
private val CardBackground = Color(0xFF151321)
private val MutedText = Color(0xFF918BA4)
private val LiveGreen = Color(0xFF62E8C9)

@Composable
fun HomeScreen(onOpenRoom: (String) -> Unit, onLogout: () -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Discover) }
    var showCreateRoom by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(
        containerColor = PageBackground,
        floatingActionButton = { if (selectedTab == HomeTab.Discover) FloatingActionButton(onClick = { showCreateRoom = true }) { Text("+") } },
        bottomBar = { PremiumBottomBar(selectedTab) { selectedTab = it } },
    ) { padding ->
        when (selectedTab) {
            HomeTab.Discover -> DiscoverScreen(onOpenRoom, Modifier.fillMaxSize().padding(padding))
            HomeTab.Messages -> MessagesScreen(Modifier.fillMaxSize().padding(padding))
            HomeTab.Profile -> ProfileTab(onLogout, Modifier.fillMaxSize().padding(padding))
        }
    }
    if (showCreateRoom) CreateRoomDialog(onDismiss = { showCreateRoom = false }, onCreate = { title, description -> scope.launch { Ses10Api.createRoom(title, "Sohbet", description).onSuccess { showCreateRoom = false; onOpenRoom(it.slug) } } })
}

@Composable
private fun PremiumBottomBar(selectedTab: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationBar(
        containerColor = Color(0xF512101B),
        tonalElevation = 0.dp,
        modifier = Modifier.background(Color(0xFF242030)).padding(horizontal = 8.dp),
    ) {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                icon = {
                    Surface(
                        color = if (selectedTab == tab) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color.Transparent,
                        shape = CircleShape,
                    ) { Text(tab.symbol, Modifier.padding(horizontal = 15.dp, vertical = 7.dp), color = if (selectedTab == tab) MaterialTheme.colorScheme.primary else MutedText) }
                },
                label = { Text(tab.label, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium) },
            )
        }
    }
}

@Composable
private fun DiscoverScreen(onOpenRoom: (String) -> Unit, modifier: Modifier = Modifier) {
    var rooms by remember { mutableStateOf(emptyList<DiscoverRoom>()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var selectedCategory by rememberSaveable { mutableStateOf("Tümü") }

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        Ses10Api.rooms()
            .onSuccess { apiRooms ->
                rooms = apiRooms.mapIndexed { index, room ->
                    val accents = listOf(Color(0xFFA879FF), Color(0xFF4B9CFF), Color(0xFF58DFC3), Color(0xFFF26FC5))
                    DiscoverRoom(room.slug, room.title, room.category.uppercase(), room.onlineCount, room.owner.ifBlank { "Oda sahibi" }, accents[index % accents.size])
                }
                loading = false
            }
            .onFailure {
                error = it.message ?: "Odalar şu anda yüklenemiyor."
                loading = false
            }
    }

    val categories = listOf("Tümü") + rooms.map { it.category }.distinct()
    val visibleRooms = if (selectedCategory == "Tümü") rooms else rooms.filter { it.category == selectedCategory }

    LazyColumn(
        modifier = modifier.background(PageBackground),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DiscoverHero(roomCount = rooms.size) }
        if (rooms.isNotEmpty()) {
            item {
                CategoryRail(categories, selectedCategory) { selectedCategory = it }
            }
        }
        item {
            SectionHeader(
                title = if (selectedCategory == "Tümü") "Şimdi canlı" else selectedCategory.lowercase().replaceFirstChar { it.uppercase() },
                count = visibleRooms.size,
            )
        }
        when {
            loading -> item { LoadingRooms() }
            error != null -> item { ErrorState(error.orEmpty()) { retryKey += 1 } }
            visibleRooms.isEmpty() -> item { EmptyRooms() }
            else -> items(visibleRooms, key = { it.slug }) { room -> RoomCard(room, onOpenRoom) }
        }
    }
}

@Composable
private fun DiscoverHero(roomCount: Int) {
    Box(
        Modifier.fillMaxWidth().height(282.dp).background(
            Brush.verticalGradient(listOf(Color(0xFF171135), Color(0xFF0D0A1B), PageBackground)),
        ).padding(horizontal = 20.dp, vertical = 20.dp),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).size(180.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(Color(0x66533EE9), Color.Transparent)),
            ),
        )
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Color(0xFFA879FF), Color(0xFF4B83FF)))), contentAlignment = Alignment.Center) {
                        Text("S", fontWeight = FontWeight.Black, color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("SES10", style = MaterialTheme.typography.titleMedium, color = Color(0xFFF5F1FF), fontWeight = FontWeight.Black)
                        Text("CANLI SOSYAL SES", color = MutedText, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Surface(color = Color(0xFF171522), shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Color(0xFF2B2738))) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(LiveGreen))
                        Spacer(Modifier.width(7.dp))
                        Text("$roomCount canlı", color = LiveGreen, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("SESİNİ BUL", color = Color(0xFFBBA8FF), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
                Text("Sohbetin tam\nortasına katıl.", style = MaterialTheme.typography.displaySmall, color = Color(0xFFFAF8FF), fontWeight = FontWeight.Black)
                Text("Yeni insanlarla tanış, canlı odaları dinle\nve hazır olduğunda mikrofonu al.", color = MutedText, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(14) { index ->
                        Box(Modifier.width(3.dp).height((7 + (index * 7 % 22)).dp).clip(CircleShape).background(if (index % 3 == 0) Color(0xFFA879FF) else Color(0xFF55DCC7)))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text("Şu an yayında", color = Color(0xFFD9D3E6), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun CategoryRail(categories: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { category ->
            val active = category == selected
            Surface(
                modifier = Modifier.clickable { onSelect(category) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) MaterialTheme.colorScheme.primary else Color(0xFF14121D),
                border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.primary else Color(0xFF292534)),
            ) {
                Text(category.lowercase().replaceFirstChar { it.uppercase() }, Modifier.padding(horizontal = 15.dp, vertical = 9.dp), color = if (active) Color(0xFF171226) else Color(0xFFC9C2D4), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("KEŞFET", color = Color(0xFFA879FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color(0xFFF5F1FF), fontWeight = FontWeight.Bold)
        }
        Text("$count oda", color = MutedText, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun RoomCard(room: DiscoverRoom, onClick: (String) -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().shadow(16.dp, shape, ambientColor = room.accent.copy(alpha = .14f), spotColor = room.accent.copy(alpha = .18f)).clickable { onClick(room.slug) },
        shape = shape,
        border = BorderStroke(1.dp, room.accent.copy(alpha = .23f)),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(room.accent.copy(alpha = .16f), Color.Transparent, Color.Transparent)))) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = room.accent.copy(alpha = .14f), shape = RoundedCornerShape(10.dp)) {
                        Text(room.category, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = room.accent, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(LiveGreen))
                        Spacer(Modifier.width(6.dp))
                        Text("CANLI", color = LiveGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                    }
                }
                Text(room.title, style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFAF8FF), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Row {
                        repeat(3) { index ->
                            Box(
                                Modifier.padding(start = if (index == 0) 0.dp else 0.dp).size(38.dp).clip(CircleShape).background(
                                    if (index == 0) Brush.linearGradient(listOf(room.accent, room.accent.copy(alpha = .5f))) else Brush.linearGradient(listOf(Color(0xFF2C2838), Color(0xFF1D1A26))),
                                ),
                                contentAlignment = Alignment.Center,
                            ) { Text(if (index == 0) room.title.take(2).uppercase() else "•", color = if (index == 0) Color.White else MutedText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(room.hosts, color = Color(0xFFE8E3F0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${room.participants} kişi dinliyor", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    Surface(color = room.accent, shape = CircleShape) {
                        Text("→", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color(0xFF100D18), fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingRooms() {
    Column(Modifier.fillMaxWidth().height(190.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
        Spacer(Modifier.height(12.dp))
        Text("Canlı odalar hazırlanıyor…", color = MutedText)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = CardBackground, border = BorderStroke(1.dp, Color(0xFF3A3045))) {
        Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Bağlantı kurulamadı", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(message, color = MutedText, style = MaterialTheme.typography.bodySmall)
            Surface(Modifier.clickable(onClick = onRetry), color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(13.dp)) {
                Text("Tekrar dene", Modifier.padding(horizontal = 18.dp, vertical = 10.dp), color = Color(0xFF171226), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyRooms() {
    Column(Modifier.fillMaxWidth().height(180.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("◌", color = Color(0xFFA879FF), style = MaterialTheme.typography.displaySmall)
        Text("Şimdilik sessiz", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Yeni bir canlı oda başladığında burada görünecek.", color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MessagesScreen(modifier: Modifier = Modifier) {
    Column(modifier.background(PageBackground).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✦", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.displaySmall)
        Text("Mesajlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Henüz bir mesajın yok.", color = MutedText)
        Text("Yeni sohbetlerin burada görünecek.", color = MutedText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileTab(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    var user by remember { mutableStateOf<com.seson.app.core.network.ApiUser?>(null) }
    LaunchedEffect(Unit) { user = Ses10Api.me().getOrNull() }
    Column(modifier.background(PageBackground).padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Profil", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary.copy(alpha = .2f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .5f))) {
            Text("S10", Modifier.padding(28.dp), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
        }
        Text(user?.displayName ?: user?.username ?: "Ses10 Kullanıcısı", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("@${user?.username.orEmpty()} · ID ${user?.id.orEmpty()}", color = MutedText)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CardBackground)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Profil alanın hazır", fontWeight = FontWeight.SemiBold)
                Text("Rol: ${user?.role ?: "user"} · Bakiye: ${user?.balance ?: 0}", color = MutedText)
            }
        }
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Çıkış yap") }
    }
}

@Composable
private fun CreateRoomDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni oda oluştur") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Oda adı") }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Açıklama (opsiyonel)") })
            Text("Görünürlük: Herkese açık", color = MutedText, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = { onCreate(title.trim(), description.trim()) }, enabled = title.trim().length >= 3) { Text("Oluştur") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}
