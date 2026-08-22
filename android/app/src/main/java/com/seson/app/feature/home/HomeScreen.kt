package com.seson.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import com.seson.app.core.network.ApiUser
import com.seson.app.core.network.Ses10Api
import com.seson.app.feature.profile.ProfileScreen
import kotlinx.coroutines.launch

private enum class HomeTab(val label:String,val icon:String){Home("Ana Sayfa","⌂"),Chat("Mesaj","✉"),Profile("Ben","♙")}
private data class UiRoom(val slug:String,val title:String,val category:String,val count:Int,val host:String,val accent:Color)
private val Bg=Color(0xFFFFF9FC);private val SurfaceWhite=Color.White;private val SoftPink=Color(0xFFFCEAF5)
private val Lavender=Color(0xFFEEE5FF);private val Purple=Color(0xFFA84FEA);private val DeepPurple=Color(0xFF7B4ED8)
private val Pink=Color(0xFFF062C0);private val Ink=Color(0xFF29232E);private val Muted=Color(0xFF8D8492)

@Composable fun HomeScreen(onOpenRoom:(String)->Unit,onLogout:()->Unit){
 var tab by rememberSaveable{mutableStateOf(HomeTab.Home)};var create by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
 Scaffold(containerColor=Bg,bottomBar={BottomBar(tab){tab=it}}){pad->
  when(tab){HomeTab.Home->Feed(onOpenRoom,{create=true},Modifier.fillMaxSize().padding(pad));HomeTab.Chat->Placeholder("Mesajlar","Henüz bir mesajın yok.",Modifier.fillMaxSize().padding(pad));HomeTab.Profile->ProfileScreen(onLogout,Modifier.fillMaxSize().padding(pad))}
 }
 if(create)CreateDialog({create=false}){t,d->scope.launch{Ses10Api.createRoom(t,"Sohbet",d).onSuccess{create=false;onOpenRoom(it.slug)}}}
}
@Composable private fun BottomBar(selected:HomeTab,onSelect:(HomeTab)->Unit)=Box(Modifier.fillMaxWidth().background(if(selected==HomeTab.Profile)Color(0xFF100A18) else Bg).padding(horizontal=18.dp,vertical=10.dp)){
 Surface(Modifier.fillMaxWidth().shadow(16.dp,RoundedCornerShape(30.dp)),shape=RoundedCornerShape(30.dp),color=DeepPurple,shadowElevation=10.dp){
  Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceAround){HomeTab.entries.forEach{tab->val active=tab==selected
   Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable{onSelect(tab)}.padding(horizontal=11.dp,vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Box(Modifier.size(40.dp,27.dp).clip(CircleShape).background(if(active)Pink.copy(.22f) else Color.Transparent),contentAlignment=Alignment.Center){Text(tab.icon,color=if(active)Pink else Color.White.copy(.82f),fontWeight=FontWeight.Black)}
    Text(tab.label,color=if(active)Pink else Color.White.copy(.7f),style=MaterialTheme.typography.labelSmall,fontWeight=if(active)FontWeight.Bold else FontWeight.Medium)
   }
  }}
 }
}
@Composable private fun Feed(open:(String)->Unit,create:()->Unit,modifier:Modifier){
 var rooms by remember{mutableStateOf(emptyList<UiRoom>())};var user by remember{mutableStateOf<ApiUser?>(null)};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};var retry by remember{mutableIntStateOf(0)}
 var category by rememberSaveable{mutableStateOf("Hot")};var search by rememberSaveable{mutableStateOf(false)};var query by rememberSaveable{mutableStateOf("")}
 LaunchedEffect(retry){loading=true;error=null;user=Ses10Api.me().getOrNull();Ses10Api.rooms().onSuccess{rs->val colors=listOf(Pink,Purple,Color(0xFFE5945B),Color(0xFF61BDA9));rooms=rs.mapIndexed{i,r->UiRoom(r.slug,r.title,r.category.ifBlank{"Sohbet"},r.onlineCount,r.owner.ifBlank{"Ses10"},colors[i%colors.size])};loading=false}.onFailure{error=it.message?:"Odalar yüklenemiyor.";loading=false}}
 val shown=rooms.filter{query.isBlank()||it.title.contains(query,true)||it.host.contains(query,true)}
 LazyColumn(modifier.background(Bg),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
  item{Header(user,create){search=!search}}
  if(search)item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=20.dp),placeholder={Text("Oda veya kullanıcı ara")},singleLine=true,shape=RoundedCornerShape(18.dp))}
  item{Tabs(category){category=it}};item{Hero(user,rooms.size)};item{Section("Visited Rooms","See all")}
  when{loading->item{Box(Modifier.fillMaxWidth().height(130.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=Purple)}};error!=null->item{ErrorState(error.orEmpty()){retry++}};shown.isEmpty()->item{Placeholder("Şimdilik sessiz","Yeni canlı odalar burada görünecek.",Modifier.fillMaxWidth().height(140.dp))};else->item{RoomRail(shown,open)}}
  item{Section("Events","View calendar")};item{Events(shown)}
 }
}
@Composable private fun Header(user:ApiUser?,create:()->Unit,search:()->Unit)=Row(Modifier.fillMaxWidth().padding(start=20.dp,end=18.dp,top=18.dp),verticalAlignment=Alignment.CenterVertically){
 Avatar(initials(user?.displayName?:user?.username?:"Ses10"),Purple,50);Column(Modifier.padding(start=11.dp).weight(1f)){Text("Ses10",color=Ink,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold);Text("ID: ${user?.id?.take(10)?:"SES10"} · ${user?.displayName?:user?.username?:"Online"}",color=Muted,style=MaterialTheme.typography.labelSmall,maxLines=1)}
 Surface(onClick=create,color=Color(0xFF63304D),shape=RoundedCornerShape(18.dp),shadowElevation=5.dp){Text("+  Create",Modifier.padding(horizontal=14.dp,vertical=10.dp),color=Color.White,fontWeight=FontWeight.Bold)};Spacer(Modifier.width(8.dp));Surface(onClick=search,color=SurfaceWhite,shape=CircleShape,shadowElevation=3.dp){Text("⌕",Modifier.padding(11.dp),color=Ink,fontWeight=FontWeight.Bold)}
}
@Composable private fun Tabs(selected:String,select:(String)->Unit)=Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf("Hot","Site","Discover").forEach{tab->Surface(Modifier.clickable{select(tab)},color=if(tab==selected)SoftPink else Color.Transparent,shape=RoundedCornerShape(18.dp)){Text(tab,Modifier.padding(horizontal=20.dp,vertical=9.dp),color=if(tab==selected)Purple else Muted,fontWeight=FontWeight.Bold)}}}
@Composable private fun Hero(user:ApiUser?,count:Int)=Box(Modifier.padding(horizontal=20.dp).fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFFB776EB),Color(0xFFE48DD1),Color(0xFFF5C5DD))))){
 Box(Modifier.size(190.dp).offset(245.dp,(-65).dp).clip(CircleShape).background(Color.White.copy(.18f)));Box(Modifier.size(105.dp).offset(280.dp,135.dp).clip(CircleShape).background(Lavender.copy(.5f)))
 Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.SpaceBetween){Row(verticalAlignment=Alignment.CenterVertically){Avatar(initials(user?.displayName?:"Ses10"),DeepPurple,48);Column(Modifier.padding(start=11.dp)){Text("Ses10 Canlı",color=Color.White,fontWeight=FontWeight.Black);Text("Sosyal ses topluluğu",color=Color.White.copy(.82f),style=MaterialTheme.typography.bodySmall)}};Column{Text("Sesinle bağlan,\nsohbete katıl.",color=Color.White,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Black);Text("$count canlı oda seni bekliyor",color=Color.White.copy(.86f))}}
}
@Composable private fun Section(title:String,action:String)=Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(title,color=Ink,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.ExtraBold);Text(action,color=Purple,fontWeight=FontWeight.Bold)}
@Composable private fun RoomRail(rooms:List<UiRoom>,open:(String)->Unit)=LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){items(rooms,key={it.slug}){r->
 Surface(Modifier.width(276.dp).height(112.dp).shadow(8.dp,RoundedCornerShape(20.dp)).clickable{open(r.slug)},color=SurfaceWhite,shape=RoundedCornerShape(20.dp)){
  Row(Modifier.fillMaxSize().padding(15.dp),verticalAlignment=Alignment.CenterVertically){Avatar(initials(r.host),r.accent,54);Column(Modifier.padding(start=12.dp).weight(1f)){Text(r.title,color=Ink,fontWeight=FontWeight.ExtraBold,maxLines=1,overflow=TextOverflow.Ellipsis);Text("ID: ${r.slug.take(9)} · ${r.host}",color=Muted,style=MaterialTheme.typography.bodySmall,maxLines=1);Spacer(Modifier.height(7.dp));Text(r.category,color=r.accent,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)};Column(horizontalAlignment=Alignment.End){Surface(color=SoftPink,shape=RoundedCornerShape(9.dp)){Text("HOST",Modifier.padding(7.dp,4.dp),color=Pink,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Black)};Spacer(Modifier.height(18.dp));Text("● ${r.count}",color=DeepPurple,fontWeight=FontWeight.Bold)}}
 }
}}
@Composable private fun Events(rooms:List<UiRoom>){val list=rooms.take(3).ifEmpty{listOf(UiRoom("ses10","Ses10 Buluşması","Topluluk",0,"Ses10",Purple))};LazyRow(contentPadding=PaddingValues(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(13.dp)){items(list,key={"event-${it.slug}"}){e->
 Box(Modifier.width(172.dp).height(205.dp).clip(RoundedCornerShape(21.dp)).background(Brush.verticalGradient(listOf(e.accent.copy(.72f),DeepPurple)))){Box(Modifier.size(110.dp).offset(95.dp,(-25).dp).clip(CircleShape).background(Color.White.copy(.15f)));Column(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.SpaceBetween){Text("SES10 EVENT",color=Color.White,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Black);Column{Text(e.title,color=Color.White,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.ExtraBold,maxLines=2);Text("Bugün · Canlı",color=Color.White.copy(.8f),style=MaterialTheme.typography.bodySmall)}}}
}}}
@Composable private fun Avatar(text:String,color:Color,size:Int)=Box(Modifier.size(size.dp).clip(CircleShape).background(Brush.linearGradient(listOf(color,color.copy(.58f)))),contentAlignment=Alignment.Center){Text(text,color=Color.White,fontWeight=FontWeight.Black)}
private fun initials(v:String)=v.trim().split(Regex("\\s+")).filter{it.isNotBlank()}.take(2).joinToString(""){it.take(1).uppercase()}.ifBlank{"S10"}
@Composable private fun ErrorState(message:String,retry:()->Unit)=Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(message,color=Muted);TextButton(onClick=retry){Text("Tekrar dene",color=Purple)}}
@Composable private fun Placeholder(title:String,detail:String,modifier:Modifier)=Column(modifier.background(Bg),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Text(title,color=Ink,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(detail,color=Muted)}
@Composable private fun CreateDialog(dismiss:()->Unit,create:(String,String)->Unit){var title by remember{mutableStateOf("")};var description by remember{mutableStateOf("")};AlertDialog(onDismissRequest=dismiss,title={Text("Yeni oda oluştur")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(title,{title=it},label={Text("Oda adı")},singleLine=true);OutlinedTextField(description,{description=it},label={Text("Açıklama")})}},confirmButton={TextButton(onClick={create(title.trim(),description.trim())},enabled=title.trim().length>=3){Text("Oluştur")}},dismissButton={TextButton(onClick=dismiss){Text("Vazgeç")}})}
