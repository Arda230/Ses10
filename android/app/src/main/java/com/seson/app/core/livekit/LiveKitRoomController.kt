package com.seson.app.core.livekit

import android.content.Context
import com.seson.app.core.network.Ses10Api
import io.livekit.android.LiveKit
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class LiveKitRoomController(context: Context, private val roomName: String) {
    private val room: Room = LiveKit.create(context.applicationContext)
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var identity: String? = null

    suspend fun connect() {
        val credentials = Ses10Api.liveKitToken(roomName)
        identity = credentials.identity
        room.connect(credentials.serverUrl, credentials.token)
        room.localParticipant.setMicrophoneEnabled(false)
    }

    suspend fun claimSeat(seatId: Int) = Ses10Api.claimSeat(roomName, seatId)

    suspend fun leaveSeat() {
        room.localParticipant.setMicrophoneEnabled(false)
        Ses10Api.leaveSeat(roomName)
    }

    suspend fun setMicrophoneEnabled(enabled: Boolean) {
        val participantIdentity = checkNotNull(identity) { "Oda bağlantısı hazır değil." }
        if (enabled) {
            Ses10Api.setMuted(roomName, participantIdentity, false)
            room.localParticipant.setMicrophoneEnabled(true)
        } else {
            room.localParticipant.setMicrophoneEnabled(false)
            Ses10Api.setMuted(roomName, participantIdentity, true)
        }
    }

    suspend fun leaveAndDisconnect() {
        runCatching { room.localParticipant.setMicrophoneEnabled(false) }
        runCatching { Ses10Api.leaveRoom(roomName) }
        room.disconnect()
    }

    fun close() {
        room.disconnect()
        cleanupScope.launch { runCatching { Ses10Api.leaveRoom(roomName) } }
    }
}
