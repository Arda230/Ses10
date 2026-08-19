package com.seson.app.core.livekit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.seson.app.BuildConfig
import com.seson.app.core.network.Ses10Api
import com.seson.app.core.network.ApiHttpException
import com.seson.app.core.network.RoomState
import com.seson.app.core.network.parseRoomState
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import io.livekit.android.util.LoggingLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import org.json.JSONObject

internal class LiveKitRoomController(context: Context, private val roomName: String) {
    private val appContext = context.applicationContext
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val room: Room = LiveKit.create(appContext)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var identity: String? = null
    private var eventResponse: Response? = null
    @Volatile private var closed = false
    @Volatile private var reconnectStartedAt: Long? = null
    private var tokenIssuedAtSeconds: Long? = null
    private var tokenExpiresAtSeconds: Long? = null
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = logNetwork("available", network)
        override fun onLost(network: Network) = logNetwork("lost", network)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) =
            logNetwork("capabilities-changed", network, capabilities)
    }
    private val mutableRoomState = MutableStateFlow<RoomState?>(null)
    private val mutableLiveKitParticipants = MutableStateFlow<Set<String>>(emptySet())
    private val mutableLiveKitMicrophones = MutableStateFlow<Set<String>>(emptySet())
    val roomState: StateFlow<RoomState?> = mutableRoomState.asStateFlow()
    val liveKitParticipants: StateFlow<Set<String>> = mutableLiveKitParticipants.asStateFlow()
    val liveKitMicrophones: StateFlow<Set<String>> = mutableLiveKitMicrophones.asStateFlow()

    init {
        if (BuildConfig.DEBUG) {
            LiveKit.loggingLevel = LoggingLevel.DEBUG
            LiveKit.enableWebRTCLogging = true
            Log.i(TAG, "diagnostic LiveKit SDK and WebRTC debug logging enabled")
        }
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
            .onFailure { Log.w(TAG, "diagnostic network callback registration failed", it) }
        cleanupScope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> { reconnectStartedAt = null; logLifecycle("CONNECTED", event) }
                    is RoomEvent.Reconnecting -> {
                        reconnectStartedAt = SystemClock.elapsedRealtime()
                        logLifecycle("RECONNECTING", event)
                        logReconnectCheckpoints()
                    }
                    is RoomEvent.Reconnected -> { logLifecycle("RECONNECTED", event); reconnectStartedAt = null }
                    is RoomEvent.Disconnected -> {
                        logLifecycle("DISCONNECTED reason=" + event.reason, event, event.error)
                        reconnectStartedAt = null
                    }
                    is RoomEvent.TrackPublicationFailed -> logLifecycle("TRACK_PUBLICATION_FAILED", event)
                    else -> {
                        val eventName = event.javaClass.simpleName
                        if (eventName.contains("Signal", true) || eventName.contains("Connection", true))
                            logLifecycle(eventName, event)
                    }
                }
                publishLiveKitState()
            }
        }
    }

    suspend fun connect() {
        closed = false
        val credentials = Ses10Api.liveKitToken(roomName)
        captureTokenTimes(credentials.token)
        Log.i(TAG, "diagnostic connect-start room=" + roomName + " " + tokenSummary())
        identity = credentials.identity
        mutableRoomState.value = credentials.state
        room.connect(credentials.serverUrl, credentials.token)
        check(awaitRoomConnected()) { "LiveKit bağlantısı 15 saniye içinde tamamlanmadı: ${room.state}" }
        room.localParticipant.setMicrophoneEnabled(false)
        publishLiveKitState()
        startRoomEvents()
    }

    suspend fun claimSeat(seatId: Int) {
        updateAuthoritativeState { Ses10Api.claimSeat(roomName, seatId) }
    }

    suspend fun leaveSeat() {
        room.localParticipant.setMicrophoneEnabled(false)
        updateAuthoritativeState { Ses10Api.leaveSeat(roomName) }
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean): Boolean {
        val participantIdentity = checkNotNull(identity) { "Oda bağlantısı hazır değil." }
        check(awaitRoomConnected(10_000)) { "LiveKit bağlantısı hazır değil: ${room.state}" }
        if (enabled) {
            check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "Android mikrofon izni verilmedi."
            }
            try {
                updateAuthoritativeState { Ses10Api.setMuted(roomName, participantIdentity, false) }
                check(awaitMicrophonePublishPermission()) { "LiveKit mikrofon yayın izni henüz hazır değil." }
                val enabledBySdk = room.localParticipant.setMicrophoneEnabled(true)
                check(enabledBySdk) { "LiveKit SDK mikrofon yayınını başlatamadı." }
                check(awaitMicrophonePublished()) { "LiveKit mikrofon track'i publish edilmedi veya muted kaldı." }
            } catch (error: Throwable) {
                runCatching { room.localParticipant.setMicrophoneEnabled(false) }
                runCatching { updateAuthoritativeState { Ses10Api.setMuted(roomName, participantIdentity, true) } }
                throw error
            }
        } else {
            val disabledBySdk = room.localParticipant.setMicrophoneEnabled(false)
            check(disabledBySdk) { "LiveKit SDK mikrofon yayınını kapatamadı." }
            check(!isMicrophonePublished()) { "LiveKit mikrofon track'i hâlâ yayında." }
            updateAuthoritativeState { Ses10Api.setMuted(roomName, participantIdentity, true) }
        }
        return isMicrophonePublished()
    }

    fun isMicrophonePublished(): Boolean {
        val publication = room.localParticipant.getTrackPublication(Track.Source.MICROPHONE)
        return room.state == Room.State.CONNECTED &&
            publication?.track != null &&
            !publication.muted &&
            room.localParticipant.isMicrophoneEnabled
    }

    private fun logLifecycle(name: String, event: Any, error: Throwable? = null) {
        val local = room.localParticipant
        val microphone = local.getTrackPublication(Track.Source.MICROPHONE)
        val reconnectMs = reconnectStartedAt?.let { SystemClock.elapsedRealtime() - it }
        val message = "diagnostic lifecycle=" + name + " roomState=" + room.state +
            " elapsedMs=" + SystemClock.elapsedRealtime() + " reconnectMs=" + reconnectMs +
            " identity=" + local.identity + " expectedIdentity=" + identity + " sid=" + local.sid +
            " remoteCount=" + room.remoteParticipants.size + " micEnabled=" + local.isMicrophoneEnabled +
            " micTrack=" + (microphone?.track != null) + " micMuted=" + microphone?.muted +
            " permissions=" + local.permissions + " " + tokenSummary() + " event=" + event
        if (error == null) Log.w(TAG, message)
        else Log.e(TAG, message + " exception=" + error.javaClass.name + ": " + error.message, error)
    }

    private fun logReconnectCheckpoints() {
        listOf(5000L, 15000L, 30000L).forEach { checkpoint ->
            cleanupScope.launch {
                delay(checkpoint)
                if (reconnectStartedAt != null) logLifecycle("RECONNECTING_CHECKPOINT_" + checkpoint + "ms", "checkpoint")
            }
        }
    }

    private fun captureTokenTimes(token: String) {
        runCatching {
            val payload = JSONObject(String(Base64.decode(token.split(".")[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)))
            tokenIssuedAtSeconds = payload.optLong("iat").takeIf { it > 0 }
            tokenExpiresAtSeconds = payload.optLong("exp").takeIf { it > 0 }
        }.onFailure { Log.w(TAG, "diagnostic token claims could not be decoded", it) }
    }

    private fun tokenSummary(): String {
        val now = System.currentTimeMillis() / 1000
        return "tokenIat=" + tokenIssuedAtSeconds + " tokenExp=" + tokenExpiresAtSeconds +
            " tokenRemainingSec=" + tokenExpiresAtSeconds?.minus(now)
    }

    private fun logNetwork(action: String, network: Network, supplied: NetworkCapabilities? = null) {
        val capabilities = supplied ?: connectivityManager.getNetworkCapabilities(network)
        val transports = listOf(
            "wifi" to NetworkCapabilities.TRANSPORT_WIFI,
            "cellular" to NetworkCapabilities.TRANSPORT_CELLULAR,
            "ethernet" to NetworkCapabilities.TRANSPORT_ETHERNET,
            "vpn" to NetworkCapabilities.TRANSPORT_VPN,
        ).filter { capabilities?.hasTransport(it.second) == true }.joinToString(",") { it.first }
        Log.w(TAG, "diagnostic network=" + action + " id=" + network + " transports=" + transports +
            " validated=" + capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) +
            " internet=" + capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) +
            " roomState=" + room.state)
    }

    private fun unregisterNetworkDiagnostics() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
    }

    private suspend fun updateAuthoritativeState(action: suspend () -> RoomState) {
        try {
            mutableRoomState.value = action()
        } catch (error: ApiHttpException) {
            if (error.status != 401) throw error
            Log.e(TAG, "diagnostic backend-action UNAUTHORIZED while LiveKit roomState=" + room.state +
                " identity=" + identity + "; refreshing backend session only", error)
            clearStaleSelfSeat()
            refreshBackendSession()
            mutableRoomState.value = action()
        }
    }

    private suspend fun refreshBackendSession() {
        val credentials = Ses10Api.liveKitToken(roomName)
        check(identity == null || identity == credentials.identity) { "LiveKit identity değişti." }
        identity = credentials.identity
        mutableRoomState.value = credentials.state
    }

    private fun clearStaleSelfSeat() {
        val current = mutableRoomState.value ?: return
        mutableRoomState.value = current.copy(
            seats = current.seats.map { seat ->
                if (seat.occupant?.identity == current.selfIdentity) seat.copy(occupant = null) else seat
            },
            selfSeatId = null,
        )
        mutableLiveKitMicrophones.value = mutableLiveKitMicrophones.value - current.selfIdentity
    }

    private fun publishLiveKitState() {
        val localIdentity = identity
        val participantIdentities = room.remoteParticipants.keys.mapTo(mutableSetOf()) { it.value }
        if (room.state == Room.State.CONNECTED && localIdentity != null) participantIdentities += localIdentity
        mutableLiveKitParticipants.value = participantIdentities
        mutableLiveKitMicrophones.value = participantIdentities.filterTo(mutableSetOf()) { participantIdentity ->
            if (participantIdentity == localIdentity) isMicrophonePublished()
            else room.getParticipantByIdentity(participantIdentity)?.getTrackPublication(Track.Source.MICROPHONE)
                ?.let { it.track != null && !it.muted } == true
        }
    }

    private fun startRoomEvents() {
        cleanupScope.launch {
            while (!closed && room.state != Room.State.DISCONNECTED) {
                runCatching {
                    Ses10Api.roomEvents(roomName).use { response ->
                        eventResponse = response
                        val source = checkNotNull(response.body).source()
                        while (!closed && !source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (line.startsWith("data: ")) mutableRoomState.value = parseRoomState(JSONObject(line.removePrefix("data: ")))
                        }
                    }
                }.onFailure { error ->
                    if (error is ApiHttpException && error.status == 401) {
                        Log.e(TAG, "diagnostic SSE UNAUTHORIZED while LiveKit roomState=" + room.state +
                            " identity=" + identity + "; possible server-side participant cleanup", error)
                        clearStaleSelfSeat()
                        runCatching { refreshBackendSession() }
                            .onFailure { Log.w(TAG, "Backend room session refresh failed", it) }
                    } else if (!closed) Log.w(TAG, "Room state stream reconnecting", error)
                }
                eventResponse = null
                if (!closed && room.state != Room.State.DISCONNECTED) delay(1_000)
            }
        }
    }

    private suspend fun awaitRoomConnected(timeoutMillis: Long = 15_000): Boolean = withTimeoutOrNull(timeoutMillis) {
        while (room.state != Room.State.CONNECTED) {
            if (room.state == Room.State.DISCONNECTED) return@withTimeoutOrNull false
            delay(50)
        }
        true
    } ?: false

    private suspend fun awaitMicrophonePublished(): Boolean = withTimeoutOrNull(5_000) {
        while (!isMicrophonePublished()) {
            if (room.state == Room.State.DISCONNECTED) return@withTimeoutOrNull false
            delay(50)
        }
        true
    } ?: false

    private suspend fun awaitMicrophonePublishPermission(): Boolean = withTimeoutOrNull(5_000) {
        while (true) {
            val permissions = room.localParticipant.permissions
            if (permissions != null && permissions.canPublish &&
                (permissions.canPublishSources.isEmpty() || Track.Source.MICROPHONE in permissions.canPublishSources)
            ) return@withTimeoutOrNull true
            delay(50)
        }
        @Suppress("UNREACHABLE_CODE")
        false
    } ?: false

    suspend fun leaveAndDisconnect() {
        closed = true
        eventResponse?.close()
        unregisterNetworkDiagnostics()
        runCatching { room.localParticipant.setMicrophoneEnabled(false) }
        runCatching { Ses10Api.leaveRoom(roomName) }
        room.disconnect()
    }

    fun close() {
        closed = true
        eventResponse?.close()
        unregisterNetworkDiagnostics()
        room.disconnect()
        cleanupScope.launch { runCatching { Ses10Api.leaveRoom(roomName) } }
    }

    private companion object {
        const val TAG = "Ses10LiveKit"
    }
}
