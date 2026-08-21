package com.example.rtspviewer

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Reproductor RTSP minimalista y a pantalla completa, usando libVLC
 * (el mismo motor que usa la app VLC). Se eligio sobre Media3/ExoPlayer
 * porque tolera mucho mejor servidores RTSP no estandar o "caseros".
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

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var statusText: TextView
    private val retryHandler = Handler(Looper.getMainLooper())
    private var retryRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pantalla siempre encendida: es una app de "monitor" de cámara.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_main)
        videoLayout = findViewById(R.id.video_layout)
        statusText = findViewById(R.id.status_text)

        videoLayout.setOnLongClickListener {
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

    // ---------- Reproducción ----------

    private fun startPlayback(url: String) {
        cancelRetry()
        releasePlayer()

        statusText.visibility = View.VISIBLE
        statusText.text = "Conectando a la cámara..."

        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--rtsp-caching=1000",
            "--network-caching=1000"
        )

        val vlc = LibVLC(this, options)
        libVLC = vlc

        val player = MediaPlayer(vlc)
        mediaPlayer = player
        player.attachViews(videoLayout, null, false, false)

        val media = Media(vlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        player.media = media
        media.release()

        player.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Playing -> statusText.visibility = View.GONE
                MediaPlayer.Event.Buffering -> {
                    if (event.buffering < 100f) {
                        statusText.visibility = View.VISIBLE
                        statusText.text = "Buffering ${event.buffering.toInt()}%..."
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Error de reproducción libVLC")
                    statusText.visibility = View.VISIBLE
                    statusText.text = "Sin señal (error de reproducción)\nReintentando..."
                    scheduleRetry(url)
                }
                MediaPlayer.Event.EndReached -> {
                    Log.w(TAG, "Stream finalizado inesperadamente")
                    statusText.visibility = View.VISIBLE
                    statusText.text = "Sin señal (stream finalizado)\nReintentando..."
                    scheduleRetry(url)
                }
                else -> {}
            }
        }

        player.play()
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
        mediaPlayer?.let { player ->
            player.stop()
            player.detachViews()
            player.release()
        }
        mediaPlayer = null
        libVLC?.release()
        libVLC = null
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
