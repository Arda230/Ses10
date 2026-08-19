package com.seson.app.core.livekit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.seson.app.core.network.Ses10Api
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal class LiveKitRoomController(context: Context, private val roomName: String) {
    private val appContext = context.applicationContext
    private val room: Room = LiveKit.create(appContext)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var identity: String? = null

    init {
        cleanupScope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.Connected -> Log.i(TAG, "LiveKit connected")
                    is RoomEvent.Reconnecting -> Log.w(TAG, "LiveKit reconnecting")
                    is RoomEvent.Reconnected -> Log.i(TAG, "LiveKit reconnected")
                    is RoomEvent.Disconnected -> Log.w(
                        TAG,
                        "LiveKit disconnected reason=${event.reason} error=${event.error?.javaClass?.simpleName ?: "none"}",
                    )
                    is RoomEvent.TrackPublicationFailed -> Log.w(TAG, "LiveKit microphone publication failed")
                    else -> Unit
                }
            }
        }
    }

    suspend fun connect() {
        val credentials = Ses10Api.liveKitToken(roomName)
        identity = credentials.identity
        room.connect(credentials.serverUrl, credentials.token)
        check(awaitRoomConnected()) { "LiveKit bağlantısı 15 saniye içinde tamamlanmadı: ${room.state}" }
        room.localParticipant.setMicrophoneEnabled(false)
    }

    suspend fun claimSeat(seatId: Int) = Ses10Api.claimSeat(roomName, seatId)

    suspend fun leaveSeat() {
        room.localParticipant.setMicrophoneEnabled(false)
        Ses10Api.leaveSeat(roomName)
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean): Boolean {
        val participantIdentity = checkNotNull(identity) { "Oda bağlantısı hazır değil." }
        check(awaitRoomConnected(10_000)) { "LiveKit bağlantısı hazır değil: ${room.state}" }
        if (enabled) {
            check(ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                "Android mikrofon izni verilmedi."
            }
            try {
                Ses10Api.setMuted(roomName, participantIdentity, false)
                check(awaitMicrophonePublishPermission()) { "LiveKit mikrofon yayın izni henüz hazır değil." }
                val enabledBySdk = room.localParticipant.setMicrophoneEnabled(true)
                check(enabledBySdk) { "LiveKit SDK mikrofon yayınını başlatamadı." }
                check(awaitMicrophonePublished()) { "LiveKit mikrofon track'i publish edilmedi veya muted kaldı." }
            } catch (error: Throwable) {
                runCatching { room.localParticipant.setMicrophoneEnabled(false) }
                runCatching { Ses10Api.setMuted(roomName, participantIdentity, true) }
                throw error
            }
        } else {
            val disabledBySdk = room.localParticipant.setMicrophoneEnabled(false)
            check(disabledBySdk) { "LiveKit SDK mikrofon yayınını kapatamadı." }
            check(!isMicrophonePublished()) { "LiveKit mikrofon track'i hâlâ yayında." }
            Ses10Api.setMuted(roomName, participantIdentity, true)
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
        runCatching { room.localParticipant.setMicrophoneEnabled(false) }
        runCatching { Ses10Api.leaveRoom(roomName) }
        room.disconnect()
    }

    fun close() {
        room.disconnect()
        cleanupScope.launch { runCatching { Ses10Api.leaveRoom(roomName) } }
    }

    private companion object {
        const val TAG = "Ses10LiveKit"
    }
}
