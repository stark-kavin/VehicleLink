package com.vehiclelink.app.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.vehiclelink.app.core.VehicleBus
import com.vehiclelink.app.listener.CompanionListenerService

/**
 * Always-available media bridge. It does NOT depend on the notification-listener
 * service being alive — any context can read/control sessions as long as our
 * NotificationListener component is enabled in Settings. The UI calls [apply]
 * directly, and so does the BLE service when the vehicle sends a button command.
 */
object MediaHub {

    private var appContext: Context? = null
    private var msm: MediaSessionManager? = null
    private var component: ComponentName? = null
    private val main = Handler(Looper.getMainLooper())
    private var active: MediaController? = null
    private var started = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() { refresh() }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> select(list.orEmpty()) }

    /** Idempotent. Safe to call before notification access is granted. */
    fun ensureStarted(context: Context) {
        if (appContext == null) appContext = context.applicationContext
        val ctx = appContext ?: return
        if (msm == null) msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        if (component == null) component = ComponentName(ctx, CompanionListenerService::class.java)
        if (started) { refresh(); return }
        val m = msm ?: return
        val c = component ?: return
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, c, main)
            select(m.getActiveSessions(c))
            started = true
        } catch (_: SecurityException) {
            // Notification access not granted yet — UI prompts for it.
            VehicleBus.postMedia(VehicleBus.MediaInfo(false, "", "", 0, 0))
        }
    }

    fun refresh() {
        val m = msm ?: return
        val c = component ?: return
        try { select(m.getActiveSessions(c)) } catch (_: SecurityException) {}
    }

    /** Apply a transport command: NEXT / PREV / PLAY / PAUSE / PLAYPAUSE. */
    fun apply(command: String) {
        val tc = active?.transportControls ?: return
        when (command.trim().uppercase()) {
            "NEXT" -> tc.skipToNext()
            "PREV", "PREVIOUS" -> tc.skipToPrevious()
            "PLAY" -> tc.play()
            "PAUSE" -> tc.pause()
            "PLAYPAUSE", "TOGGLE" ->
                if (active?.playbackState?.state == PlaybackState.STATE_PLAYING) tc.pause() else tc.play()
        }
    }

    fun hasActive(): Boolean = active != null

    /** Packages that register a MediaSession but are not music (nav voice, assistant). */
    private val IGNORED_PKGS = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.google.android.googlequicksearchbox",
    )

    private fun select(controllers: List<MediaController>) {
        val cands = controllers.filter { it.packageName !in IGNORED_PKGS }
        val next = cands.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: cands.firstOrNull { !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() }
            ?: cands.firstOrNull()

        if (next?.sessionToken == active?.sessionToken) { publish(); return }
        active?.unregisterCallback(controllerCallback)
        active = next
        active?.registerCallback(controllerCallback, main)
        publish()
    }

    /**
     * PlaybackState.position is only the position at the player's last state update; while playing,
     * advance it by the elapsed time × speed or the progress bar lags behind the song.
     */
    private fun currentPosition(ps: PlaybackState?): Long {
        if (ps == null) return 0L
        if (ps.state != PlaybackState.STATE_PLAYING || ps.lastPositionUpdateTime <= 0L) return ps.position
        val elapsed = SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime
        return ps.position + (elapsed * ps.playbackSpeed).toLong()
    }

    private fun publish() {
        val c = active
        val md = c?.metadata
        val ps = c?.playbackState
        if (c == null || md == null) {
            VehicleBus.postMedia(VehicleBus.MediaInfo(false, "", "", 0, 0))
            return
        }
        VehicleBus.postMedia(
            VehicleBus.MediaInfo(
                playing = ps?.state == PlaybackState.STATE_PLAYING,
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = (md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
                positionMs = currentPosition(ps),
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                app = c.packageName.orEmpty(),
            )
        )
    }
}
