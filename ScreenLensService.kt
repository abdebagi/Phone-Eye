package com.example.screenlens

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlin.math.max

class ScreenLensService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        private const val CHANNEL_ID = "ScreenLensChannel"
        private const val NOTIF_ID = 1001
    }

    private lateinit var windowManager: WindowManager
    private var floatingButton: View? = null
    private var lensContainer: FrameLayout? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // إعدادات العدسة
    private var lensShape = LensShape.CIRCLE // CIRCLE, SQUARE, RECTANGLE
    private var lensScale = 2.0f
    private var lensWidth = 300
    private var lensHeight = 300

    // التحكم في النقرات
    private var tapCount = 0
    private val tapHandler = Handler(Looper.getMainLooper())
    private val resetTapRunnable = Runnable { tapCount = 0 }
    private var isLensVisible = true
    private var sleepUntil = 0L // إذا كان في وضع السكون

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())

        val dm = resources.displayMetrics
        screenWidth = dm.widthPixels
        screenHeight = dm.heightPixels
        screenDensity = dm.densityDpi
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startProjection(resultCode, data)
                    showFloatingButton()
                    showLens()
                }
            }
            ACTION_STOP -> {
                stopEverything()
                stopSelf()
            }
        }
        return START_STICKY
    }

    // ---------- MediaProjection ----------
    private fun startProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopEverything() }
        }, mainHandler)

        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenLensDisplay",
            screenWidth, screenHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    // ---------- العدسة (Lens) ----------
    private fun showLens() {
        if (lensContainer != null) return
        val lensView = LensOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(lensWidth, lensHeight)
        }
        lensContainer = FrameLayout(this).apply {
            addView(lensView)
        }

        val params = WindowManager.LayoutParams(
            lensWidth, lensHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 200
        }

        windowManager.addView(lensContainer, params)
        applyLensShape()
        startLensRendering(lensView)
        isLensVisible = true
    }

    private fun startLensRendering(lensView: LensOverlayView) {
        val renderRunnable = object : Runnable {
            override fun run() {
                if (!isLensVisible || System.currentTimeMillis() < sleepUntil) {
                    mainHandler.postDelayed(this, 100)
                    return
                }
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth
                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    lensView.updateSource(cropped, lensScale)
                    image.close()
                }
                mainHandler.postDelayed(this, 80) // ~12 إطار/ثانية
            }
        }
        mainHandler.post(renderRunnable)
    }

    private fun applyLensShape() {
        lensContainer?.let { container ->
            val lp = container.layoutParams as WindowManager.LayoutParams
            lp.width = lensWidth
            lp.height = lensHeight
            windowManager.updateViewLayout(container, lp)
            container.removeAllViews()
            val lensView = LensOverlayView(this).apply {
                layoutParams = FrameLayout.LayoutParams(lensWidth, lensHeight)
                shape = lensShape
            }
            container.addView(lensView)
            startLensRendering(lensView)
        }
    }

    // ---------- الزر العائم (التحكم) ----------
    private fun showFloatingButton() {
        if (floatingButton != null) return
        floatingButton = FloatingButtonView(this).apply {
            setOnTapListener { count ->
                handleTaps(count)
            }
        }

        val size = 120
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - size - 30
            y = screenHeight / 2
        }
        windowManager.addView(floatingButton, params)
    }

    private fun handleTaps(count: Int) {
        when (count) {
            1 -> { // نقرة واحدة: تفعيل العدسة (إذا كانت في سكون)
                if (System.currentTimeMillis() < sleepUntil || !isLensVisible) {
                    sleepUntil = 0L
                    if (!isLensVisible) showLens() else lensContainer?.visibility = View.VISIBLE
                    isLensVisible = true
                }
            }
            3 -> { // ثلاث نقرات: سكون لمدة محددة (30 ثانية افتراضيًا)
                sleepUntil = System.currentTimeMillis() + 30_000L
                lensContainer?.visibility = View.INVISIBLE
                isLensVisible = false
            }
        }
    }

    // ---------- دورة الحياة ----------
    private fun stopEverything() {
        lensContainer?.let { windowManager.removeView(it) }
        floatingButton?.let { windowManager.removeView(it) }
        lensContainer = null
        floatingButton = null
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        virtualDisplay = null
        imageReader = null
        mediaProjection = null
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    // ---------- الإشعار ----------
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Screen Lens",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("العدسة المكبرة نشطة")
            .setContentText("اضغط على الزر العائم للتحكم")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }
}

enum class LensShape { CIRCLE, SQUARE, RECTANGLE }
