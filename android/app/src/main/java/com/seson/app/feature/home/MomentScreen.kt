package com.seson.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val MomentBackground = Color(0xFFFFF9FC)
private val MomentSurface = Color.White
private val MomentSoftPink = Color(0xFFFCEAF5)
private val MomentLavender = Color(0xFFEEE5FF)
private val MomentPurple = Color(0xFFA84FEA)
private val MomentDeepPurple = Color(0xFF7B4ED8)
private val MomentPink = Color(0xFFF062C0)
private val MomentInk = Color(0xFF29232E)
private val MomentMuted = Color(0xFF8D8492)

private data class MomentPost(
    val user: String,
    val id: String,
    val time: String,
    val initials: String,
    val text: String,
    val likes: Int,
    val comments: Int,
    val accent: Color,
    val twoImages: Boolean,
)

@Composable
internal fun MomentScreen(modifier: Modifier = Modifier) {
    var selectedFeed by rememberSaveable { mutableStateOf("Hot") }
    val posts = remember {
        listOf(
            MomentPost("Ses10 Topluluğu", "ses10club", "2 dk", "S10", "Bugünün en güzel sohbetleri burada başlıyor. Sesini paylaş, yeni insanlarla tanış ✨", 248, 36, MomentPink, true),
            MomentPost("Lila Sohbet", "lilasohbet", "18 dk", "LS", "Akşam buluşmasına hazır mısın? Sıcacık bir sohbet ve güzel müzikler için Ses10'dayız.", 184, 21, MomentDeepPurple, false),
        )
    }

    Box(modifier.background(MomentBackground)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { MomentHeader(selectedFeed, { selectedFeed = it }) }
            items(posts.size) { index -> MomentPostCard(posts[index], index) }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 22.dp).size(58.dp).shadow(12.dp, CircleShape),
            shape = CircleShape,
            color = Color.Transparent,
        ) {
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(MomentPurple, MomentPink))),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Light)
            }
        }
    }
}

@Composable
private fun MomentHeader(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 18.dp, top = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Hot", "New").forEach { tab ->
                Surface(
                    onClick = { onSelect(tab) },
                    color = if (selected == tab) MomentSoftPink else Color.Transparent,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        tab,
                        Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                        color = if (selected == tab) MomentPurple else MomentMuted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
        Surface(color = Color(0xFF63304D), shape = RoundedCornerShape(18.dp), shadowElevation = 5.dp) {
            Text("+  Create", Modifier.padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Surface(color = MomentSurface, shape = CircleShape, shadowElevation = 3.dp) {
            Text("⌕", Modifier.padding(11.dp), color = MomentInk, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MomentPostCard(post: MomentPost, index: Int) {
    Surface(
        modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(20.dp)),
        color = MomentSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MomentAvatar(post.initials, post.accent)
                Column(Modifier.padding(start = 11.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(post.user, color = MomentInk, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(5.dp))
                        Box(Modifier.size(16.dp).clip(CircleShape).background(MomentPurple), contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
                        }
                    }
                    Text("@${post.id} · ${post.time}", color = MomentMuted, style = MaterialTheme.typography.bodySmall)
                }
                Surface(color = MomentLavender, shape = RoundedCornerShape(14.dp)) {
                    Text("Follow", Modifier.padding(horizontal = 13.dp, vertical = 7.dp), color = MomentDeepPurple, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            Text(post.text, color = MomentInk, style = MaterialTheme.typography.bodyMedium)

            if (post.twoImages) {
                Row(Modifier.fillMaxWidth().height(188.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MomentImage(Modifier.weight(1f).fillMaxHeight(), post.accent, "VOICE")
                    MomentImage(Modifier.weight(1f).fillMaxHeight(), MomentPurple, "TOGETHER")
                }
            } else {
                MomentImage(Modifier.fillMaxWidth().height(224.dp), post.accent, if (index == 1) "LIVE TALK" else "SES10")
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("♡", color = MomentPink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text("${post.likes}", color = MomentMuted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(22.dp))
                Text("◯", color = MomentDeepPurple, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(5.dp))
                Text("${post.comments}", color = MomentMuted, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("⋯", color = MomentMuted, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MomentAvatar(text: String, accent: Color) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accent, MomentDeepPurple))),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MomentImage(modifier: Modifier, accent: Color, label: String) {
    Box(
        modifier.clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(accent.copy(.72f), MomentDeepPurple))),
    ) {
        Box(Modifier.size(120.dp).offset(65.dp, (-28).dp).clip(CircleShape).background(Color.White.copy(.14f)))
        Box(Modifier.size(84.dp).align(Alignment.BottomStart).offset((-20).dp, 30.dp).clip(CircleShape).background(MomentSoftPink.copy(.22f)))
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text("SES10", color = Color.White.copy(.76f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        }
    }
}
