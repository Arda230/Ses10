package com.seson.app.core.network

import android.content.Context
import android.content.SharedPreferences

import com.seson.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class ApiUser(val id: String, val username: String, val displayName: String, val avatarUrl: String?, val role: String, val balance: Int)
internal data class ApiRoom(val slug: String, val title: String, val category: String, val owner: String, val status: String, val onlineCount: Int)
internal data class RoomSeatOccupant(val identity: String, val name: String, val muted: Boolean)
internal data class RoomSeatState(val id: Int, val locked: Boolean, val occupant: RoomSeatOccupant?)
internal data class ApiMessage(val id: String, val userId: String?, val displayName: String, val body: String, val type: String, val createdAt: String)
internal data class ApiParticipant(val userId: String, val identity: String, val name: String, val role: String, val seatId: Int?)
internal data class HandRaise(val userId: String, val identity: String, val name: String)
internal data class GiftInfo(val id: String, val name: String, val price: Int, val assetIdentifier: String)
internal data class RoomState(
    val seats: List<RoomSeatState>,
    val messages: List<ApiMessage>,
    val participants: List<ApiParticipant>,
    val handRaises: List<HandRaise>,
    val selfRole: String,
    val closed: Boolean,
    val participantCount: Int,
    val selfIdentity: String,
    val selfSeatId: Int?,
)
internal class ApiHttpException(val status: Int, val code: String) : IOException(code)
internal data class LiveKitCredentials(val serverUrl: String, val token: String, val identity: String, val state: RoomState)

internal object Ses10Api {
    private val cookieJar = PersistentCookieJar()
    fun initialize(context: Context) = cookieJar.initialize(context.getSharedPreferences("ses10_session", Context.MODE_PRIVATE))
    private val client = OkHttpClient.Builder().cookieJar(cookieJar).build()
    private val eventClient = client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun login(login: String, password: String): Result<Unit> = runCatching {
        post("/api/auth/login", JSONObject().put("login", login).put("password", password)); Unit
    }

    suspend fun register(username: String, email: String, password: String): Result<Unit> = runCatching {
        post("/api/auth/register", JSONObject().put("username", username).put("email", email).put("password", password)); Unit
    }

    suspend fun me(): Result<ApiUser?> = runCatching {
        val user = get("/api/auth/me").optJSONObject("user") ?: return@runCatching null
        ApiUser(user.getString("id"), user.getString("username"), user.optString("displayName", user.getString("username")), user.optString("avatarUrl").takeIf { it.isNotBlank() }, user.optString("role", "user"), user.optInt("balance", 0))
    }

    suspend fun logout(): Result<Unit> = runCatching { post("/api/auth/logout", JSONObject()); cookieJar.clear(); Unit }

    suspend fun createRoom(title: String, category: String, description: String = ""): Result<ApiRoom> = runCatching {
        parseRoom(post("/api/rooms", JSONObject().put("title", title).put("category", category).put("description", description)).getJSONObject("room"))
    }

    suspend fun rooms(): Result<List<ApiRoom>> = runCatching {
        val payload = get("/api/rooms")
        val array = payload.getJSONArray("rooms")
        List(array.length()) { index ->
            val room = array.getJSONObject(index)
            parseRoom(room)
        }
    }

    private fun parseRoom(room: JSONObject) = ApiRoom(room.getString("slug"), room.getString("title"), room.getString("category"), room.optJSONObject("owner")?.optString("username").orEmpty(), room.optString("status", "open"), room.optInt("onlineCount", 0))

    suspend fun liveKitToken(roomName: String): LiveKitCredentials {
        val payload = post("/api/livekit/token", JSONObject().put("roomName", roomName))
        return LiveKitCredentials(
            serverUrl = payload.getString("serverUrl"),
            token = payload.getString("token"),
            identity = payload.getJSONObject("state").getJSONObject("self").getString("identity"),
            state = parseRoomState(payload.getJSONObject("state")),
        )
    }

    fun roomEvents(roomName: String): Response {
        val request = Request.Builder().url("$baseUrl/api/rooms/$roomName/events").header("Accept", "text/event-stream").get().build()
        return eventClient.newCall(request).execute().also { response ->
            if (!response.isSuccessful) {
                val status = response.code
                response.close()
                throw ApiHttpException(status, if (status == 401) "UNAUTHORIZED" else "ROOM_EVENTS_FAILED")
            }
        }
    }

    suspend fun claimSeat(roomName: String, seatId: Int): RoomState =
        parseRoomState(post("/api/rooms/$roomName/seats/$seatId/claim", JSONObject()))

    suspend fun leaveSeat(roomName: String): RoomState =
        parseRoomState(request("/api/rooms/$roomName/seat", "DELETE", JSONObject()))

    suspend fun setMuted(roomName: String, identity: String, muted: Boolean): RoomState =
        parseRoomState(post("/api/rooms/$roomName/mute", JSONObject().put("targetIdentity", identity).put("muted", muted)))

    suspend fun sendMessage(roomName: String, text: String): ApiMessage { val item = post("/api/rooms/$roomName/messages", JSONObject().put("body", text)).getJSONObject("message"); return parseMessage(item) }
    suspend fun gifts(): Pair<List<GiftInfo>, Int> { val payload = get("/api/gifts"); val items = payload.getJSONArray("gifts"); return List(items.length()) { val gift = items.getJSONObject(it); GiftInfo(gift.getString("id"), gift.getString("name"), gift.getInt("price"), gift.getString("assetIdentifier")) } to payload.getInt("balance") }
    suspend fun sendGift(roomName: String, receiverUserId: String, giftId: String, requestId: String): Int = post("/api/rooms/$roomName/gifts", JSONObject().put("receiverUserId", receiverUserId).put("giftId", giftId).put("quantity", 1).put("requestId", requestId)).getInt("balance")
    suspend fun raiseHand(roomName: String, raised: Boolean): RoomState = parseRoomState(post("/api/rooms/$roomName/hand-raise", JSONObject().put("raised", raised)))
    suspend fun resolveHand(roomName: String, identity: String, accepted: Boolean): RoomState = parseRoomState(post("/api/rooms/$roomName/hand-raise/resolve", JSONObject().put("targetIdentity", identity).put("accepted", accepted)))
    suspend fun setSeatLock(roomName: String, seatId: Int, locked: Boolean): RoomState = parseRoomState(post("/api/rooms/$roomName/seats/$seatId/lock", JSONObject().put("locked", locked)))
    suspend fun removeFromSeat(roomName: String, identity: String): RoomState = parseRoomState(request("/api/rooms/$roomName/seat", "DELETE", JSONObject().put("targetIdentity", identity)))
    suspend fun kick(roomName: String, identity: String): RoomState = parseRoomState(post("/api/rooms/$roomName/kick", JSONObject().put("targetIdentity", identity)))
    suspend fun setRole(roomName: String, userId: String, role: String): RoomState = parseRoomState(post("/api/rooms/$roomName/roles", JSONObject().put("targetUserId", userId).put("role", role)))
    suspend fun closeRoom(roomName: String) { post("/api/rooms/$roomName/close", JSONObject()) }
    suspend fun publicProfile(userId: String): ApiUser { val user = get("/api/users/$userId").getJSONObject("user"); return ApiUser(user.getString("id"), user.getString("username"), user.getString("displayName"), user.optString("avatarUrl").takeIf { it.isNotBlank() }, user.optString("role", "user"), 0) }

    suspend fun leaveRoom(roomName: String) {
        request("/api/rooms/$roomName/participants/me", "DELETE", null)
    }

    private suspend fun get(path: String) = request(path, "GET", null)
    private suspend fun post(path: String, body: JSONObject) = request(path, "POST", body)

    private suspend fun request(path: String, method: String, body: JSONObject?): JSONObject = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(baseUrl + path)
        val requestBody = body?.toString()?.toRequestBody(jsonType)
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(requestBody ?: "{}".toRequestBody(jsonType))
            "DELETE" -> builder.delete(requestBody)
            else -> error("Desteklenmeyen HTTP metodu")
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val payload = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (!response.isSuccessful) {
                val raw = payload.opt("error")
                val message = when (raw) {
                    is JSONObject -> raw.optString("message", "İstek tamamlanamadı.")
                    is String -> raw
                    else -> "İstek tamamlanamadı (${response.code})."
                }
                throw ApiHttpException(response.code, message)
            }
            payload
        }
    }
}

internal fun parseMessage(item: JSONObject) = ApiMessage(item.getString("id"), item.optString("userId").takeIf { it.isNotBlank() && it != "null" }, item.getString("displayName"), item.getString("body"), item.getString("type"), item.getString("createdAt"))

internal fun parseRoomState(payload: JSONObject): RoomState {
    val seats = payload.getJSONArray("seats")
    return RoomState(
        messages = payload.optJSONArray("messages")?.let { array -> List(array.length()) { parseMessage(array.getJSONObject(it)) } }.orEmpty(),
        participants = payload.optJSONArray("participants")?.let { array -> List(array.length()) { val p = array.getJSONObject(it); ApiParticipant(p.getString("userId"), p.getString("identity"), p.getString("name"), p.getString("role"), p.optInt("seatId").takeIf { _ -> !p.isNull("seatId") }) } }.orEmpty(),
        handRaises = payload.optJSONArray("handRaises")?.let { array -> List(array.length()) { val h = array.getJSONObject(it); HandRaise(h.getString("userId"), h.getString("identity"), h.getString("name")) } }.orEmpty(),
        selfRole = payload.getJSONObject("self").getString("role"),
        closed = payload.optBoolean("closed", false),
        seats = List(seats.length()) { index ->
            val seat = seats.getJSONObject(index)
            val occupant = seat.optJSONObject("occupant")?.let {
                RoomSeatOccupant(it.getString("identity"), it.getString("name"), it.getBoolean("muted"))
            }
            RoomSeatState(seat.getInt("id"), seat.getBoolean("locked"), occupant)
        },
        participantCount = payload.getInt("participantCount"),
        selfIdentity = payload.getJSONObject("self").getString("identity"),
        selfSeatId = payload.getJSONObject("self").takeUnless { it.isNull("seatId") }?.getInt("seatId"),
    )
}

private class PersistentCookieJar : CookieJar {
    private var preferences: SharedPreferences? = null
    fun initialize(value: SharedPreferences) = synchronized(cookies) { preferences = value; cookies.clear(); value.getStringSet("cookies", emptySet()).orEmpty().mapNotNullTo(cookies) { Cookie.parse(BuildConfig.API_BASE_URL.toHttpUrl(), it) } }
    fun clear() = synchronized(cookies) { cookies.clear(); preferences?.edit()?.remove("cookies")?.apply() }
    private val cookies = mutableListOf<Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this.cookies) {
            this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
            this.cookies.addAll(cookies)
            preferences?.edit()?.putStringSet("cookies", this.cookies.map { it.toString() }.toSet())?.apply()
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        cookies.filter { it.matches(url) }
    }
}
