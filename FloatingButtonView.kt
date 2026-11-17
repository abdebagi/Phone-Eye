package com.example.screenlens

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class FloatingButtonView(context: Context) : View(context) {

    private val gestureDetector: GestureDetector
    private var tapCount = 0
    private val handler = Handler(Looper.getMainLooper())
    private val resetRunnable = Runnable { tapCount = 0 }

    private var onTapListener: ((Int) -> Unit)? = null
    private var onLongPressListener: (() -> Unit)? = null

    init {
        setBackgroundResource(R.drawable.bg_floating_button)
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                tapCount++
                handler.removeCallbacks(resetRunnable)
                handler.postDelayed({
                    onTapListener?.invoke(tapCount)
                    tapCount = 0
                }, 300) // نافذة زمنية لتجميع النقرات
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                onLongPressListener?.invoke()
            }
        })
    }

    fun setOnTapListener(listener: (Int) -> Unit) { onTapListener = listener }
    fun setOnLongPressListener(listener: () -> Unit) { onLongPressListener = listener }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }
}
