package com.seson.app.feature.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seson.app.core.network.Ses10Api

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

@Composable
fun HomeScreen(onOpenRoom: (String) -> Unit) {
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Discover) }
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF12101A)) {
                HomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Text(tab.symbol) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            HomeTab.Discover -> DiscoverScreen(onOpenRoom, Modifier.fillMaxSize().padding(padding))
            HomeTab.Messages -> MessagesScreen(Modifier.fillMaxSize().padding(padding))
            HomeTab.Profile -> ProfileTab(Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun DiscoverScreen(onOpenRoom: (String) -> Unit, modifier: Modifier = Modifier) {
    var rooms by remember { mutableStateOf(listOf(
        DiscoverRoom("geceye-bir-sarki", "Geceye Bir Şarkı", "MÜZİK & SOHBET", 128, "Mert, Ece ve 126 kişi", Color(0xFF9B7BFF)),
        DiscoverRoom("yeni-sesler", "Yeni Sesler Sahnesi", "TOPLULUK", 64, "Selin, Deniz ve 62 kişi", Color(0xFF4B8DFF)),
        DiscoverRoom("kahve-molasi", "Kahve Molası", "GÜNDELİK", 38, "Lara ve 37 kişi", Color(0xFF58D8C4)),
        DiscoverRoom("gece-kusagi", "Gece Kuşağı", "SERBEST SOHBET", 91, "Arda, Mina ve 89 kişi", Color(0xFFC565FF)),
    )) }
    LaunchedEffect(Unit) {
        Ses10Api.rooms().onSuccess { apiRooms ->
            if (apiRooms.isNotEmpty()) rooms = apiRooms.mapIndexed { index, room ->
                val accents = listOf(Color(0xFF9B7BFF), Color(0xFF4B8DFF), Color(0xFF58D8C4), Color(0xFFC565FF))
                DiscoverRoom(room.slug, room.title, room.category.uppercase(), 1, "Canlı oda", accents[index % accents.size])
            }
        }
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { DiscoverHero() }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Şimdi canlı", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("${rooms.size} oda", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(rooms) { room -> RoomCard(room, onOpenRoom) }
        item { Spacer(Modifier.height(14.dp)) }
    }
}

@Composable
private fun DiscoverHero() {
    Box(
        Modifier.fillMaxWidth().height(210.dp).background(
            Brush.radialGradient(listOf(Color(0x665B3FD0), Color(0x221A3B7A), Color.Transparent)),
        ).padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("SES10", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("Sesini bul.\nSohbete katıl.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Canlı odaları keşfet, dinle ve bağlan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.align(Alignment.TopEnd).size(72.dp).shadow(22.dp, CircleShape, ambientColor = Color(0xFF7657FF), spotColor = Color(0xFF4B8DFF)).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF8066FF), Color(0xFF315FD6)))), contentAlignment = Alignment.Center) {
            Text("10", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoomCard(room: DiscoverRoom, onClick: (String) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Card(
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().shadow(12.dp, shape, ambientColor = room.accent.copy(alpha = .25f), spotColor = room.accent.copy(alpha = .3f)).clickable { onClick(room.slug) },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF191622)),
    ) {
        Box(Modifier.background(Brush.linearGradient(listOf(room.accent.copy(alpha = .18f), Color.Transparent)))) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(room.category, color = room.accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF6FE7D4)))
                        Spacer(Modifier.width(6.dp))
                        Text("CANLI", color = Color(0xFF6FE7D4), style = MaterialTheme.typography.labelMedium)
                    }
                }
                Text(room.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(room.accent.copy(alpha = .3f)), contentAlignment = Alignment.Center) { Text(room.title.take(2).uppercase(), color = room.accent, style = MaterialTheme.typography.labelMedium) }
                    Spacer(Modifier.width(10.dp))
                    Column { Text(room.hosts, style = MaterialTheme.typography.bodyMedium); Text("${room.participants} kişi dinliyor", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun MessagesScreen(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Mesajlar", style = MaterialTheme.typography.headlineMedium)
        Text("Henüz bir mesajın yok.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Mesajlaşma altyapısı sonraki aşamada bağlanacak.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ProfileTab(modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Profil", Modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineMedium)
        Text("S10", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text("Ses10 Kullanıcısı", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Profil iskeleti hazır"); Text("Gerçek kullanıcı bilgileri auth bağlantısından sonra gösterilecek.", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
    }
}
