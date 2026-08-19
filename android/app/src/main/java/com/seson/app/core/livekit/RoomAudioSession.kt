package com.seson.app.core.livekit

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal object RoomAudioSession {
    private val connectionMutex = Mutex()
    private var activeRoomName: String? = null
    private var activeController: LiveKitRoomController? = null
    private var connected = false

    fun controller(context: Context, roomName: String): LiveKitRoomController = synchronized(this) {
        val existing = activeController
        if (existing != null && activeRoomName == roomName) return@synchronized existing
        existing?.close()
        LiveKitRoomController(context.applicationContext, roomName).also {
            activeRoomName = roomName
            activeController = it
            connected = false
        }
    }

    fun startForegroundService(context: Context, roomName: String) {
        controller(context, roomName)
        ContextCompat.startForegroundService(
            context,
            Intent(context, RoomAudioService::class.java).putExtra(RoomAudioService.EXTRA_ROOM_NAME, roomName),
        )
    }

    suspend fun ensureConnected(context: Context, roomName: String): LiveKitRoomController = connectionMutex.withLock {
        val controller = controller(context, roomName)
        if (!connected) {
            controller.connect()
            connected = true
        }
        controller
    }

    suspend fun leaveAndStop(context: Context) = connectionMutex.withLock {
        val controller = synchronized(this) { activeController }
        controller?.leaveAndDisconnect()
        synchronized(this) {
            activeController = null
            activeRoomName = null
            connected = false
        }
        context.stopService(Intent(context, RoomAudioService::class.java))
    }

    fun onServiceDestroyed() {
        val controller = synchronized(this) {
            activeController.also {
                activeController = null
                activeRoomName = null
                connected = false
            }
        }
        controller?.close()
    }
}
