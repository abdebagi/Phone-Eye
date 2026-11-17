package com.example.screenlens

import android.content.Context
import android.graphics.*
import android.view.View

class LensOverlayView(context: Context) : View(context) {

    private var sourceBitmap: Bitmap? = null
    private var scale = 2.0f
    var shape: LensShape = LensShape.CIRCLE

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#2196F3")
    }
    private val path = Path()
    private val matrix = Matrix()

    fun updateSource(bitmap: Bitmap, scaleFactor: Float) {
        sourceBitmap = bitmap
        scale = scaleFactor
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = sourceBitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()

        path.reset()
        when (shape) {
            LensShape.CIRCLE -> path.addCircle(w / 2, h / 2, minOf(w, h) / 2, Path.Direction.CW)
            LensShape.SQUARE -> path.addRect(0f, 0f, w, h, Path.Direction.CW)
            LensShape.RECTANGLE -> path.addRoundRect(
                0f, 0f, w, h, 20f, 20f, Path.Direction.CW
            )
        }

        canvas.save()
        canvas.clipPath(path)

        // مركز المصدر: منتصف الشاشة تقريبًا (يمكن ربطه بموضع اللمس لاحقًا)
        val srcCx = bmp.width / 2f
        val srcCy = bmp.height / 2f
        val srcW = w / scale
        val srcH = h / scale
        val srcRect = RectF(
            srcCx - srcW / 2, srcCy - srcH / 2,
            srcCx + srcW / 2, srcCy + srcH / 2
        )

        canvas.drawBitmap(bmp, srcRect, RectF(0f, 0f, w, h), paint)
        canvas.restore()

        canvas.drawPath(path, borderPaint)
    }
}
