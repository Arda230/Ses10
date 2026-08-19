package com.seson.app.core.network

import com.seson.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

internal data class ApiRoom(val slug: String, val title: String, val category: String)
internal data class RoomSeatOccupant(val identity: String, val name: String, val muted: Boolean)
internal data class RoomSeatState(val id: Int, val occupant: RoomSeatOccupant?)
internal data class RoomState(
    val seats: List<RoomSeatState>,
    val participantCount: Int,
    val selfIdentity: String,
    val selfSeatId: Int?,
)
internal class ApiHttpException(val status: Int, val code: String) : IOException(code)
internal data class LiveKitCredentials(val serverUrl: String, val token: String, val identity: String, val state: RoomState)

internal object Ses10Api {
    private val cookieJar = MemoryCookieJar()
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

    suspend fun rooms(): Result<List<ApiRoom>> = runCatching {
        val payload = get("/api/rooms")
        val array = payload.getJSONArray("rooms")
        List(array.length()) { index ->
            val room = array.getJSONObject(index)
            ApiRoom(room.getString("slug"), room.getString("title"), room.getString("category"))
        }
    }

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

internal fun parseRoomState(payload: JSONObject): RoomState {
    val seats = payload.getJSONArray("seats")
    return RoomState(
        seats = List(seats.length()) { index ->
            val seat = seats.getJSONObject(index)
            val occupant = seat.optJSONObject("occupant")?.let {
                RoomSeatOccupant(it.getString("identity"), it.getString("name"), it.getBoolean("muted"))
            }
            RoomSeatState(seat.getInt("id"), occupant)
        },
        participantCount = payload.getInt("participantCount"),
        selfIdentity = payload.getJSONObject("self").getString("identity"),
        selfSeatId = payload.getJSONObject("self").takeUnless { it.isNull("seatId") }?.getInt("seatId"),
    )
}

private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this.cookies) {
            this.cookies.removeAll { old -> cookies.any { it.name == old.name && it.domain == old.domain && it.path == old.path } }
            this.cookies.addAll(cookies)
        }
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        cookies.filter { it.matches(url) }
    }
}
