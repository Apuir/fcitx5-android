package org.fcitx.fcitx5.android.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.util.AttributeSet
import org.fcitx.fcitx5.android.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * SphereRippleView - 球形中心发散声波样式（低功耗、线程冬眠优化版）
 * 中心一个克制果冻球，声音转化为向四周扩散、边缘渐隐的涟漪声波。
 */
class SphereRippleView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RenderView(context, attrs, defStyleAttr), IWaveAnimView {

    private companion object {
        private const val DEFAULT_OFFSET_SPEED = 200f
        private const val DEFAULT_SENSIBILITY = 5
        // 性能微调：连续 30 帧（约 500ms）音量归零，即判定进入冬眠期，剔除 CPU 时间片分配
        private const val SILENT_IDLE_THRESHOLD = 30
    }

    private var offsetSpeed = DEFAULT_OFFSET_SPEED
    private var volume = 0f
    private var targetVolume = 0
    private var perVolume = 0f
    private var sensibility = DEFAULT_SENSIBILITY

    // 🌟 低功耗控制状态机
    private val renderLock = Object()
    private var silentFrameCount = 0
    @Volatile private var isEngineSleeping = false
    @Volatile private var isViewAttached = false

    var backGroundColor: Int = Color.WHITE
        set(value) {
            field = value
            isTransparentMode = value == Color.TRANSPARENT
        }

    var lineColor: Int = Color.parseColor("#2ED184")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    private var viewWidth = 0
    private var viewHeight = 0
    private var centerX = 0f
    private var centerY = 0f

    private var baseRadius = 0f
    private var maxRippleRadius = 0f

    private val rippleProgress = floatArrayOf(0.0f, 0.33f, 0.66f)
    private var isTransparentMode = false

    init {
        initAttr(attrs)
        setZOrderOnTop(true)
        holder?.setFormat(PixelFormat.TRANSLUCENT)
    }

    private fun initAttr(attrs: AttributeSet?) {
        val t = context.obtainStyledAttributes(attrs, R.styleable.WaveLineView)
        backGroundColor = t.getColor(R.styleable.WaveLineView_wlvBackgroundColor, Color.WHITE)
        lineColor = t.getColor(R.styleable.WaveLineView_wlvLineColor, Color.parseColor("#2ED184"))
        offsetSpeed = t.getFloat(R.styleable.WaveLineView_wlvMoveSpeed, DEFAULT_OFFSET_SPEED)
        sensibility = t.getInt(R.styleable.WaveLineView_wlvSensibility, DEFAULT_SENSIBILITY)
        isTransparentMode = backGroundColor == Color.TRANSPARENT
        t.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        this.viewWidth = w
        this.viewHeight = h
        if (w == 0 || h == 0) return

        centerX = w / 2f
        centerY = h / 2f

        baseRadius = min(w, h) * 0.12f
        maxRippleRadius = min(w, h) * 0.55f
        perVolume = sensibility * 0.45f
    }

    override fun doDrawBackground(canvas: Canvas) {
        if (isTransparentMode) {
            canvas.drawColor(backGroundColor, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(backGroundColor)
        }
    }

    override fun onRender(canvas: Canvas, millisPassed: Long) {
        if (viewWidth == 0 || viewHeight == 0 || baseRadius <= 0) return

        // 🌟 核心省电熔断：若当前处于冬眠唤醒等待状态，直接阻塞并交出 CPU 执行权
        synchronized(renderLock) {
            while (isEngineSleeping && isViewAttached) {
                try {
                    renderLock.wait()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return
                }
            }
        }

        softerChangeVolume()
        val haloTimeFactor = millisPassed / offsetSpeed

        // 智能静音冬眠判定
        if (targetVolume == 0 && volume == 0f) {
            silentFrameCount++
            if (silentFrameCount >= SILENT_IDLE_THRESHOLD) {
                // 冻结前，渲染最后一帧静态场景（保持中心核心果冻球完整，防止画面突兀黑屏）
                drawStaticScene(canvas, haloTimeFactor)

                synchronized(renderLock) {
                    isEngineSleeping = true
                }
                silentFrameCount = 0
                return
            }
        } else {
            silentFrameCount = 0
        }

        drawActiveScene(canvas, haloTimeFactor)
    }

    /**
     * 活跃状态全动态声波绘制
     */
    private fun drawActiveScene(canvas: Canvas, haloTimeFactor: Float) {
        val vPercent = this.volume * 0.01f

        val r = Color.red(lineColor)
        val g = Color.green(lineColor)
        val b = Color.blue(lineColor)

        // 1. 外部发散的声波涟漪
        paint.style = Paint.Style.STROKE
        paint.shader = null

        val dynamicSpeed = 0.005f + (0.023f * vPercent)
        val currentMaxRadius =
            baseRadius + (maxRippleRadius - baseRadius) * (0.25f + 0.75f * vPercent)

        for (i in rippleProgress.indices) {
            rippleProgress[i] += dynamicSpeed
            if (rippleProgress[i] > 1.0f) {
                rippleProgress[i] = 0.0f
            }

            val progress = rippleProgress[i]
            val rippleRadius = baseRadius + (currentMaxRadius - baseRadius) * progress
            val alphaFactor = 1.0f - progress

            var rippleAlpha = (160 * alphaFactor * (0.15f + 0.85f * vPercent)).toInt()
            if (rippleAlpha < 0) rippleAlpha = 0

            paint.strokeWidth = 3.5f * (1.0f - progress * 0.6f)
            paint.color = Color.argb(rippleAlpha, r, g, b)

            canvas.drawCircle(centerX, centerY, rippleRadius, paint)
        }

        // 2. 环境氛围晕圈
        paint.style = Paint.Style.FILL
        val haloPulse = sin(haloTimeFactor * 1.2f).toFloat() * (baseRadius * 0.06f)
        paint.color = Color.argb(35, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius * 1.3f + haloPulse, paint)

        // 3. 最核心克制圆圈
        paint.color = Color.argb(166, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius, paint)
    }

    /**
     * 极致冬眠：仅保留中心氛围与基准球体，避免计算发散的 Ripple 数组步长
     */
    private fun drawStaticScene(canvas: Canvas, haloTimeFactor: Float) {
        val r = Color.red(lineColor)
        val g = Color.green(lineColor)
        val b = Color.blue(lineColor)

        paint.style = Paint.Style.FILL
        val haloPulse = sin(haloTimeFactor * 1.2f).toFloat() * (baseRadius * 0.06f)
        paint.color = Color.argb(35, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius * 1.3f + haloPulse, paint)

        paint.color = Color.argb(166, r, g, b)
        canvas.drawCircle(centerX, centerY, baseRadius, paint)
    }

    private fun softerChangeVolume() {
        val localPerVolume = perVolume
        val localTargetVolume = targetVolume
        if (volume < localTargetVolume - localPerVolume) {
            volume += localPerVolume
        } else if (volume > localTargetVolume + localPerVolume) {
            // 🌟 修正原版旧逻辑：滑落至 0f 即可，原版强制卡在死区导致无法完美触发冬眠
            volume = (volume - localPerVolume).coerceAtLeast(0f)
        } else {
            volume = localTargetVolume.toFloat()
        }
    }

    override fun stopAnim() {
        super.stopAnim()
        clearDraw()
        // 🌟 防死锁：当停止动画时，若后台线程正处于 wait 阻塞中，强制解锁使其平稳退出
        synchronized(renderLock) {
            isEngineSleeping = false
            renderLock.notifyAll()
        }
    }

    fun clearDraw() {
        var canvas: Canvas? = null
        try {
            canvas = holder?.lockCanvas(null)
            canvas?.let {
                if (isTransparentMode) {
                    it.drawColor(backGroundColor, PorterDuff.Mode.CLEAR)
                } else {
                    it.drawColor(backGroundColor)
                }
            }
        } catch (ignored: Exception) {
        } finally {
            canvas?.let { holder?.unlockCanvasAndPost(it) }
        }
    }

    fun setMoveSpeed(moveSpeed: Float) {
        this.offsetSpeed = moveSpeed
    }

    override fun setWaveformColor(color: Int) {
        this.lineColor = color
    }

    override fun setVolume(volume: Int) {
        val inputVolume = volume.coerceIn(0, 100)
        if (abs(this.targetVolume - inputVolume) > perVolume || inputVolume > 0) {
            this.targetVolume = inputVolume + 20
            checkVolumeValue()

            // 🌟 主动唤醒：当输入音量信号大于 0 且引擎处于挂起状态，立刻释放锁，通知渲染线程开工
            if (inputVolume > 0 && isEngineSleeping) {
                synchronized(renderLock) {
                    isEngineSleeping = false
                    renderLock.notifyAll()
                }
            }
        }
    }

    fun setSensibility(sensibility: Int) {
        this.sensibility = sensibility
        checkSensibilityValue()
    }

    override fun release() {
        stopAnim()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isViewAttached = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isViewAttached = false
        release()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            stopAnim()
        } else {
            // 恢复焦点时打碎可能存在的休眠锁
            synchronized(renderLock) {
                isEngineSleeping = false
                renderLock.notifyAll()
            }
            startAnim()
        }
    }

    private fun checkVolumeValue() {
        if (targetVolume > 100) targetVolume = 100
        if (targetVolume < 0) targetVolume = 0
    }

    private fun checkSensibilityValue() {
        if (sensibility > 10) sensibility = 10
        if (sensibility < 1) sensibility = 1
    }
}