package cl.ccu.tiempos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class FloatingTimerService : Service() {
    companion object {
        const val ACTION_STOP_TIMER = "cl.ccu.tiempos.ACTION_STOP_TIMER"
        private const val CHANNEL_ID = "ccu_floating_timer"
    }

    private lateinit var windowManager: WindowManager
    private var rootView: LinearLayout? = null
    private lateinit var txtPatente: TextView
    private lateinit var txtEett: TextView
    private lateinit var txtTimer: TextView

    private var idRegistro: String = ""
    private var startEpochMs: Long = 0L
    private var metaMin: Int = 45

    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            val diff = (System.currentTimeMillis() - startEpochMs).coerceAtLeast(0L)
            val mins = (diff / 60000L).toInt()
            val secs = ((diff % 60000L) / 1000L).toInt()
            txtTimer.text = String.format("%02d:%02d", mins, secs)
            txtTimer.setTextColor(if (diff > metaMin * 60000L) Color.parseColor("#f87171") else Color.parseColor("#4ade80"))
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        idRegistro = intent?.getStringExtra("idRegistro") ?: ""
        val patente = intent?.getStringExtra("patente") ?: "CAMIÓN"
        val eett = intent?.getStringExtra("eett") ?: ""
        startEpochMs = intent?.getLongExtra("startEpochMs", System.currentTimeMillis()) ?: System.currentTimeMillis()
        metaMin = intent?.getIntExtra("metaMin", 45) ?: 45

        startForeground(101, buildNotification(patente))
        showOrUpdateOverlay(patente, eett)
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Temporizador Flotante", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(patente: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("CCU Tiempos: $patente")
            .setContentText("Cronómetro flotante activo")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .build()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun showOrUpdateOverlay(patente: String, eett: String) {
        if (rootView != null) {
            txtPatente.text = "🚛 $patente"
            txtEett.text = eett
            return
        }

        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#0f172a"))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(2), Color.parseColor("#007a33"))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = bg
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        txtPatente = TextView(this).apply {
            text = "🚛 $patente"
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnClose = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94a3b8"))
            textSize = 16f
            setPadding(dp(8), 0, dp(2), 0)
            setOnClickListener { stopSelf() }
        }

        header.addView(txtPatente)
        header.addView(btnClose)

        txtEett = TextView(this).apply {
            text = eett
            setTextColor(Color.parseColor("#cbd5e1"))
            textSize = 11f
            maxLines = 1
        }

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, 0)
        }

        txtTimer = TextView(this).apply {
            text = "00:00"
            setTextColor(Color.parseColor("#4ade80"))
            textSize = 22f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 0, dp(12), 0)
        }

        val btnBg = GradientDrawable().apply {
            setColor(Color.parseColor("#dc2626"))
            cornerRadius = dp(8).toFloat()
        }

        val btnStop = Button(this).apply {
            text = "⏹ PARAR"
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            background = btnBg
            setPadding(dp(12), dp(6), dp(12), dp(6))
            minHeight = 0
            minimumHeight = 0
            setOnClickListener {
                val stopIntent = Intent(ACTION_STOP_TIMER).apply {
                    setPackage(packageName)
                    putExtra("idRegistro", idRegistro)
                }
                sendBroadcast(stopIntent)
                stopSelf()
            }
        }

        bottom.addView(txtTimer)
        bottom.addView(btnStop)

        container.addView(header)
        container.addView(txtEett)
        container.addView(bottom)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(120)
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        container.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(container, params)
                        return true
                    }
                }
                return false
            }
        })

        rootView = container
        windowManager.addView(container, params)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        rootView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        rootView = null
        super.onDestroy()
    }
}
