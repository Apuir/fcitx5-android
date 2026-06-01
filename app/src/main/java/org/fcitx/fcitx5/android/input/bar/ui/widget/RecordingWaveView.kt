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
import timber.log.Timber

class RecordingWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var breathProgress = 0f
    private var animating = false

    private val rectF = RectF()

    private val baseSize = context.dp(4f)
    private val maxHeight = context.dp(12f)
    private val dotSpacing = context.dp(12f)
    private val cornerRadius = baseSize / 2f

    private val animator by lazy {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 750L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()

            addUpdateListener {
                breathProgress = it.animatedValue as Float
                invalidate()
            }
        }
    }

    fun setThemeColor(theme: Theme) {
        paint.color = theme.keyTextColor and 0x00FFFFFF or 0xE6000000.toInt()
        invalidate()
    }

    constructor(context: Context, theme: Theme) : this(context) {
        setThemeColor(theme)
    }

    fun startAnimation() {
        if (animating) return

        animating = true

        if (!animator.isStarted) {
            animator.start()
        } else {
            animator.resume()
        }
    }

    fun stopAnimation() {
        if (!animating) return

        animating = false

        animator.cancel()

        breathProgress = 0f

        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
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

        if (!animating) {
            drawMorphingDot(canvas, leftCenterX, centerY, baseSize)
            drawMorphingDot(canvas, middleCenterX, centerY, baseSize)
            drawMorphingDot(canvas, rightCenterX, centerY, baseSize)
            return
        }

        val sideHeight = baseSize + (maxHeight - baseSize) * breathProgress

        val middleHeight = baseSize + (maxHeight - baseSize) * (1f - breathProgress)

        drawMorphingDot(canvas, leftCenterX, centerY, sideHeight)
        drawMorphingDot(canvas, middleCenterX, centerY, middleHeight)
        drawMorphingDot(canvas, rightCenterX, centerY, sideHeight)
    }

    private fun drawMorphingDot(
        canvas: Canvas, cx: Float, cy: Float, currentHeight: Float
    ) {
        val left = cx - (baseSize / 2f)
        val right = cx + (baseSize / 2f)
        val top = cy - (currentHeight / 2f)
        val bottom = cy + (currentHeight / 2f)

        rectF.set(left, top, right, bottom)

        canvas.drawRoundRect(
            rectF, cornerRadius, cornerRadius, paint
        )
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            startAnimation()
        } else {
            stopAnimation()
        }
    }
}