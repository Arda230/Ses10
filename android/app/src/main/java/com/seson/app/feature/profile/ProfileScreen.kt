package com.seson.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seson.app.core.network.ApiUser
import com.seson.app.core.network.Ses10Api

private val ProfileBg = Color(0xFF100A18)
private val ProfileSurface = Color(0xB820162B)
private val ProfileSurfaceSoft = Color(0x8F2C1D39)
private val ProfilePurple = Color(0xFFA84FEA)
private val ProfilePink = Color(0xFFF062C0)
private val ProfileGold = Color(0xFFFFD278)
private val ProfileText = Color(0xFFFFF7FF)
private val ProfileMuted = Color(0xFFC0ADC8)

private data class ProfileMocks(
    val followers: String = "1.2K",
    val following: String = "248",
    val visitors: String = "3.8K",
    val bio: String = "Sesin ritmini yakala, iyi sohbetin peşinden git. 🎙️",
    val level: Int = 18,
    val familyName: String? = "Neon Sesler",
    val familyLevel: Int = 7,
)

private val menuItems = listOf(
    "◈" to "Cüzdan", "↗" to "Arkadaşlarını Davet Et", "✦" to "Başarılar / Madalyalar",
    "◆" to "Seviye", "♢" to "Aile", "◇" to "Mağaza", "▣" to "Eşyalarım",
)
private val settingsItems = listOf("文" to "Dil", "♡" to "Geri Bildirim", "⚙" to "Ayarlar")

@Composable
fun ProfileScreen(onLogout: () -> Unit, modifier: Modifier = Modifier) {
    var user by remember { mutableStateOf<ApiUser?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    // TODO(backend): Replace profile statistics, biography, cover, level, badges and family mocks with profile API fields.
    val mocks = remember { ProfileMocks() }

    LaunchedEffect(Unit) {
        Ses10Api.me().onSuccess { user = it }.onFailure { error = it.message ?: "Profil yüklenemedi." }
        loading = false
    }

    Box(modifier.background(ProfileBg)) {
        Box(Modifier.fillMaxWidth().height(310.dp).background(Brush.verticalGradient(listOf(Color(0xFF3C1858), Color(0xFF1A1026), ProfileBg))))
        Box(Modifier.size(220.dp).offset(x = 230.dp, y = (-55).dp).shadow(40.dp, CircleShape, ambientColor = ProfilePink, spotColor = ProfilePurple).clip(CircleShape).background(ProfilePink.copy(.12f)))
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { ProfileHero(user, mocks, clipboard::setText) }
            item { SocialStats(mocks) }
            item { AboutCard(mocks.bio) }
            item { LevelAndBadges(mocks.level) }
            item { FamilyCard(mocks.familyName, mocks.familyLevel) }
            item { MenuCard(menuItems, user?.balance) }
            item { MenuCard(settingsItems, null) }
            item {
                TextButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                    Text("Çıkış yap", color = ProfilePink, fontWeight = FontWeight.Bold)
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = ProfilePurple, trackColor = ProfileSurface) }
            error?.let { message -> item { Text(message, color = ProfilePink, fontSize = 12.sp) } }
        }
    }
}

@Composable
private fun ProfileHero(user: ApiUser?, mocks: ProfileMocks, copyId: (AnnotatedString) -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xD9371C4D), Color(0xB8241833), Color(0xD04B1F4A))))
            .shadow(20.dp, RoundedCornerShape(28.dp), ambientColor = ProfilePurple.copy(.3f), spotColor = ProfilePink.copy(.2f)),
    ) {
        // TODO(backend): Render the optional cover image when the profile API exposes coverUrl.
        Box(Modifier.size(150.dp).align(Alignment.TopEnd).offset(35.dp, (-42).dp).clip(CircleShape).background(ProfilePink.copy(.14f)))
        Surface(
            onClick = { /* TODO(profile-edit): Navigate to the profile editor. */ },
            modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
            color = ProfileSurfaceSoft,
            shape = RoundedCornerShape(14.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, ProfilePink.copy(.35f)),
        ) { Text("✎  Profili Düzenle", Modifier.padding(horizontal = 11.dp, vertical = 8.dp), color = ProfileText, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
        Row(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(94.dp).shadow(18.dp, CircleShape, ambientColor = ProfilePurple, spotColor = ProfilePink)
                    .clip(CircleShape).background(Brush.linearGradient(listOf(ProfilePurple, ProfilePink)))
                    .padding(3.dp).clip(CircleShape).background(Color(0xFF21142D)),
                contentAlignment = Alignment.Center,
            ) {
                // TODO(profile-image): Load user.avatarUrl with the shared image pipeline when it is added.
                Text(initials(user?.displayName ?: user?.username ?: "Ses10"), color = ProfileText, fontSize = 27.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.padding(start = 15.dp).weight(1f)) {
                Text(user?.displayName?.ifBlank { user.username } ?: "Ses10 Kullanıcısı", color = ProfileText, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("@${user?.username ?: "ses10"}", color = ProfilePink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { copyId(AnnotatedString(user?.id ?: "000000")) }.background(Color.Black.copy(.18f)).padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ses10 ID: ${user?.id?.take(12) ?: "000000"}", color = ProfileMuted, fontSize = 10.sp, maxLines = 1)
                    Text("  ⧉", color = ProfilePink, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SocialStats(mocks: ProfileMocks) = GlassCard {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Takipçi" to mocks.followers, "Takip Edilen" to mocks.following, "Ziyaretçi" to mocks.visitors).forEachIndexed { index, item ->
            Column(Modifier.weight(1f).clickable { /* TODO(navigation): Open the selected social list. */ }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.second, color = ProfileText, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(item.first, color = ProfileMuted, fontSize = 10.sp)
            }
            if (index < 2) Box(Modifier.width(1.dp).height(34.dp).background(ProfilePurple.copy(.22f)))
        }
    }
}

@Composable
private fun AboutCard(bio: String) = GlassCard {
    SectionHeader("Hakkımda", "Düzenle")
    Text(bio, color = ProfileMuted, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun LevelAndBadges(level: Int) = GlassCard {
    SectionHeader("Seviye ve Başarılar", "Tümünü Gör")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(ProfilePurple, ProfilePink))), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("LV", color = ProfileText, fontSize = 9.sp, fontWeight = FontWeight.Bold); Text(level.toString(), color = ProfileText, fontSize = 20.sp, fontWeight = FontWeight.Black) }
        }
        listOf("✦", "♛", "◆", "⚡").forEachIndexed { index, badge ->
            Box(Modifier.size(43.dp).shadow(8.dp, CircleShape, ambientColor = if (index == 1) ProfileGold else ProfilePurple).clip(CircleShape).background(if (index == 1) ProfileGold.copy(.16f) else ProfilePurple.copy(.15f)), contentAlignment = Alignment.Center) {
                Text(badge, color = if (index == 1) ProfileGold else ProfilePink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FamilyCard(name: String?, level: Int) = GlassCard {
    SectionHeader("Aile", "Görüntüle")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(ProfilePink.copy(.8f), ProfilePurple))), contentAlignment = Alignment.Center) { Text("S10", color = ProfileText, fontSize = 11.sp, fontWeight = FontWeight.Black) }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(name ?: "Bir aileye katıl", color = ProfileText, fontWeight = FontWeight.Bold)
            Text(if (name == null) "Ses10 topluluğunu keşfet" else "Aile seviyesi $level", color = ProfileMuted, fontSize = 11.sp)
        }
        Text("›", color = ProfilePink, fontSize = 24.sp)
    }
}

@Composable
private fun MenuCard(items: List<Pair<String, String>>, balance: Int?) = GlassCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
    items.forEachIndexed { index, item ->
        Row(Modifier.fillMaxWidth().clickable { /* TODO(navigation): Connect account menu destinations. */ }.padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(ProfilePurple.copy(.13f)), contentAlignment = Alignment.Center) { Text(item.first, color = ProfilePink, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Text(item.second, Modifier.padding(start = 12.dp).weight(1f), color = ProfileText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            if (item.second == "Cüzdan" && balance != null) Text(balance.toString(), color = ProfileGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("  ›", color = ProfileMuted, fontSize = 21.sp)
        }
        if (index < items.lastIndex) HorizontalDivider(color = Color.White.copy(.07f))
    }
}

@Composable
private fun GlassCard(contentPadding: PaddingValues = PaddingValues(16.dp), content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(22.dp), ambientColor = ProfilePurple.copy(.12f), spotColor = ProfilePink.copy(.08f))
            .clip(RoundedCornerShape(22.dp)).background(ProfileSurface)
            .then(Modifier.background(Brush.linearGradient(listOf(Color.White.copy(.035f), Color.Transparent))))
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SectionHeader(title: String, action: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
    Text(title, color = ProfileText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    Text(action, Modifier.clickable { /* TODO(navigation): Connect section action. */ }.padding(4.dp), color = ProfilePink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
}

private fun initials(value: String) = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "S10" }
