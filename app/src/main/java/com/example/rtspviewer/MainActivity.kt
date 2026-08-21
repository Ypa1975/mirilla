package com.example.rtspviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.android.exoplayer2.ExoPlaybackException
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource
import com.google.android.exoplayer2.ui.PlayerView

/**
 * Reproductor RTSP minimalista y a pantalla completa.
 *
 * - Guarda la URL RTSP en SharedPreferences (mantén pulsado en la pantalla
 *   para cambiarla).
 * - Reconecta automáticamente si se corta la señal o falla la conexión.
 * - Modo inmersivo: oculta barra de estado y de navegación.
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "RTSPViewer"
        private const val PREFS = "rtsp_prefs"
        private const val KEY_URL = "rtsp_url"
        private const val RETRY_DELAY_MS = 5000L

        // URL precargada por defecto. Mantén pulsada la pantalla para
        // cambiarla desde la propia app en cualquier momento.
        private const val DEFAULT_URL = "rtsp://192.168.1.146:8554/mirilla"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pantalla siempre encendida: es una app de "monitor" de cámara.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        playerView = findViewById(R.id.player_view)
        statusText = findViewById(R.id.status_text)

        playerView.setOnLongClickListener {
            showUrlDialog()
            true
        }

        hideSystemUi()
    }

    override fun onStart() {
        super.onStart()
        val url = getSavedUrl()
        if (url.isNullOrBlank()) {
            showUrlDialog()
        } else {
            startPlayback(url)
        }
    }

    override fun onStop() {
        super.onStop()
        cancelRetry()
        releasePlayer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Doble función del botón "menú"/volumen no se usa; evita salidas
        // accidentales con back en pantallas táctiles antiguas si se desea.
        return super.onKeyDown(keyCode, event)
    }

    // ---------- Reproducción ----------

    private fun startPlayback(url: String) {
        cancelRetry()
        releasePlayer()

        statusText.visibility = View.VISIBLE
        statusText.text = "Conectando a la cámara..."

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer
        playerView.player = exoPlayer

        val mediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true) // TCP es más estable en redes WiFi con pérdidas
            .createMediaSource(MediaItem.fromUri(url))

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> statusText.visibility = View.GONE
                    Player.STATE_BUFFERING -> statusText.apply {
                        visibility = View.VISIBLE
                        text = "Buffering..."
                    }
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Error de reproducción: ${error.message}", error)
                statusText.visibility = View.VISIBLE
                statusText.text = "Sin señal. Reintentando..."
                scheduleRetry(url)
            }
        })

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    private fun scheduleRetry(url: String) {
        cancelRetry()
        val runnable = Runnable { startPlayback(url) }
        retryRunnable = runnable
        retryHandler.postDelayed(runnable, RETRY_DELAY_MS)
    }

    private fun cancelRetry() {
        retryRunnable?.let { retryHandler.removeCallbacks(it) }
        retryRunnable = null
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playerView.player = null
    }

    // ---------- Configuración de la URL ----------

    private fun getSavedUrl(): String? =
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_URL, DEFAULT_URL)

    private fun saveUrl(url: String) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URL, url)
            .apply()
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_URI
            hint = "rtsp://usuario:clave@192.168.1.10:554/stream1"
            setText(getSavedUrl() ?: "")
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("URL de la cámara RTSP")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Guardar") { _, _ ->
                val url = input.text.toString().trim()
                if (url.startsWith("rtsp://")) {
                    saveUrl(url)
                    startPlayback(url)
                } else {
                    Toast.makeText(this, "La URL debe empezar con rtsp://", Toast.LENGTH_LONG).show()
                    showUrlDialog()
                }
            }
            .show()
    }

    // ---------- Pantalla completa inmersiva ----------

    private fun hideSystemUi() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }
}
