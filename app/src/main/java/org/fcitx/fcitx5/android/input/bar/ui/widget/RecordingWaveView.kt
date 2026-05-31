package org.fcitx.fcitx5.android.input.bar.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import org.fcitx.fcitx5.android.data.theme.Theme
import splitties.dimensions.dp

class RecordingWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var animator: ValueAnimator? = null
    private var breathProgress = 0f
    private val rectF = RectF()

    private val baseSize = context.dp(4f)
    private val maxHeight = context.dp(12f)
    private val dotSpacing = context.dp(12f)
    private val cornerRadius = baseSize / 2f

    fun setThemeColor(theme: Theme) {
        paint.color = theme.keyTextColor and 0x00FFFFFF or 0xE6000000.toInt()
        invalidate()
    }

    constructor(context: Context, theme: Theme) : this(context) {
        setThemeColor(theme)
    }

    fun startAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                breathProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val centerX = w / 2f
        val centerY = h / 2f

        val leftCenterX = centerX - dotSpacing
        val middleCenterX = centerX
        val rightCenterX = centerX + dotSpacing

        if (animator == null) {
            drawMorphingDot(canvas, leftCenterX, centerY, baseSize)
            drawMorphingDot(canvas, middleCenterX, centerY, baseSize)
            drawMorphingDot(canvas, rightCenterX, centerY, baseSize)
            return
        }

        // 交错互补形态计算
        val sideHeight = baseSize + (maxHeight - baseSize) * breathProgress
        val middleHeight = baseSize + (maxHeight - baseSize) * (1f - breathProgress)

        // 绘制三点
        drawMorphingDot(canvas, leftCenterX, centerY, sideHeight)
        drawMorphingDot(canvas, middleCenterX, centerY, middleHeight)
        drawMorphingDot(canvas, rightCenterX, centerY, sideHeight)
    }

    private fun drawMorphingDot(canvas: Canvas, cx: Float, cy: Float, currentHeight: Float) {
        val left = cx - (baseSize / 2f)
        val right = cx + (baseSize / 2f)
        val top = cy - (currentHeight / 2f)
        val bottom = cy + (currentHeight / 2f)

        rectF.set(left, top, right, bottom)
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
    }
}