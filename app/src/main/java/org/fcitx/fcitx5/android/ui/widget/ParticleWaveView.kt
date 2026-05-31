package org.fcitx.fcitx5.android.ui.widget

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import org.fcitx.fcitx5.android.R
import java.util.Random
import kotlin.math.*

/**
 * ParticleWaveView - 粒子喷射环风格声波动效（低功耗、硬件加速友好版）
 * 中心多边形核心 + 声音驱动的喷射粒子 + 旋转能量环
 */
class ParticleWaveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RenderView(context, attrs, defStyleAttr), IWaveAnimView {

    private companion object {
        private const val SILENT_IDLE_THRESHOLD = 30 // 约 500ms 无声则判定冬眠
        private const val RAD_CONVERT = PI.toFloat() / 180f // 缓存弧度转换常数，免去高频双精度转换
    }

    private var offsetSpeed: Float = 600f
    @Volatile private var volume = 0f
    @Volatile private var targetVolume = 0
    @Volatile private var perVolume = 0f
    private var sensibility = 5
    private var polygonSides = 8

    // 线程阻塞锁状态机
    private val renderLock = Object()
    private var silentFrameCount = 0
    @Volatile private var isEngineSleeping = false
    @Volatile private var isViewAttached = false

    @Volatile private var _backgroundColor = Color.BLACK
    @Volatile private var _lineColor = Color.parseColor("#00D4FF")

    var backGroundColor: Int
        get() = _backgroundColor
        set(value) {
            _backgroundColor = value
            isTransparentMode = value == Color.TRANSPARENT
        }

    var lineColor: Int
        get() = _lineColor
        set(value) {
            _lineColor = value
            dirtyGradient = true // 颜色改变时，标记渐变对象需要刷新
        }

    // 🌟 复用图形对象，严禁在 onRender 内部实例化
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val polygonPath = Path()
    private val innerPath = Path()
    private var coreGradient: RadialGradient? = null
    private var dirtyGradient = true
    private var lastGradientRadius = 0f

    private var viewWidth = 0
    private var viewHeight = 0
    private var centerX = 0f
    private var centerY = 0f
    private var baseRadius = 0f
    private var maxRadius = 0f

    // 粒子系统
    private val particles = mutableListOf<Particle>()
    private val random = Random()
    private val particleCount = 30

    private var ringRotation = 0f
    private var coreRotation = 0f
    private var isTransparentMode = false

    init {
        initAttr(attrs)
        initParticles()
    }

    private fun initAttr(attrs: AttributeSet?) {
        val t = context.obtainStyledAttributes(attrs, R.styleable.WaveLineView)
        _backgroundColor = t.getColor(R.styleable.WaveLineView_wlvBackgroundColor, Color.BLACK)
        _lineColor = t.getColor(R.styleable.WaveLineView_wlvLineColor, Color.parseColor("#00D4FF"))
        offsetSpeed = t.getFloat(R.styleable.WaveLineView_wlvMoveSpeed, 200f)
        sensibility = t.getInt(R.styleable.WaveLineView_wlvSensibility, 5)
        polygonSides = t.getInt(R.styleable.WaveLineView_wlvPolygonSides, 8).coerceIn(6, 12)
        isTransparentMode = _backgroundColor == Color.TRANSPARENT
        t.recycle()

        setZOrderOnTop(true)
        holder?.setFormat(PixelFormat.TRANSLUCENT)
    }

    private fun initParticles() {
        particles.clear()
        for (i in 0 until particleCount) {
            particles.add(Particle().also { resetParticle(it, true) })
        }
    }

    private fun resetParticle(p: Particle, randomStart: Boolean) {
        p.angle = random.nextFloat() * 2 * PI.toFloat()
        p.radius = 0f
        p.speed = 0.5f + random.nextFloat() * 1.5f
        p.size = 2f + random.nextFloat() * 3f
        p.life = if (randomStart) random.nextFloat() else 0f
        p.active = randomStart && random.nextFloat() > 0.7f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        if (w == 0 || h == 0) return

        centerX = w / 2f
        centerY = h / 2f
        baseRadius = min(w, h) * 0.12f
        maxRadius = min(w, h) * 0.48f
        perVolume = sensibility * 0.45f
        dirtyGradient = true
    }

    override fun doDrawBackground(canvas: Canvas) {
        if (isTransparentMode) {
            canvas.drawColor(_backgroundColor, PorterDuff.Mode.CLEAR)
        } else {
            canvas.drawColor(_backgroundColor)
        }
    }

    override fun onRender(canvas: Canvas, millisPassed: Long) {
        if (viewWidth == 0 || viewHeight == 0 || baseRadius <= 0) return

        // 🌟 性能安全熔断：若当前已被判定为静音冬眠，立刻阻塞挂起，交出 CPU 时间片
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
        val vPercent = volume * 0.01f
        val timeFactor = millisPassed / offsetSpeed

        // 智能静音判定机
        if (targetVolume == 0 && volume == 0f) {
            silentFrameCount++
            if (silentFrameCount >= SILENT_IDLE_THRESHOLD) {
                // 冬眠前最后渲染一次基础静态框，保留核心 UI 并进入挂起状态
                drawStaticScene(canvas, timeFactor)
                synchronized(renderLock) {
                    isEngineSleeping = true
                }
                silentFrameCount = 0
                return
            }
        } else {
            silentFrameCount = 0
        }

        drawActiveScene(canvas, vPercent, timeFactor)
    }

    /**
     * 活跃状态高频全功能绘制
     */
    private fun drawActiveScene(canvas: Canvas, vPercent: Float, timeFactor: Float) {
        val r = Color.red(_lineColor)
        val g = Color.green(_lineColor)
        val b = Color.blue(_lineColor)

        // ========== 1. 外围旋转能量环 ==========
        val ringSpeed = 0.3f + vPercent * 2.7f
        ringRotation += ringSpeed
        if (ringRotation > 360f) ringRotation -= 360f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f + vPercent * 3f

        // 限制 step 步长永不小于 8，彻底抹除外部意外篡改 vPercent 导致 step <= 0 的死循环隐患
        val step = if (vPercent > 0.5f) 8 else 20

        for (layer in 0 until 2) {
            val ringRadius = maxRadius * (0.50f + layer * 0.2f)
            val ringAngle = if (layer == 0) ringRotation else -ringRotation
            val ringAlpha = (80 + 120 * vPercent).toInt()
            paint.color = Color.argb(ringAlpha, r, g, b)

            var a = 0
            while (a < 360) {
                // 优化：剔除 Math.toRadians 的 Double 转换开销，改用 Float 常数相乘
                val angleRad = (ringAngle + a) * RAD_CONVERT
                val cosA = cos(angleRad)
                val sinA = sin(angleRad)

                val x1 = centerX + (ringRadius - 6) * cosA
                val y1 = centerY + (ringRadius - 6) * sinA
                val x2 = centerX + ringRadius * cosA
                val y2 = centerY + ringRadius * sinA
                canvas.drawLine(x1, y1, x2, y2, paint)
                a += step
            }

            if (vPercent > 0.6f) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(150, r, g, b)
                var a2 = 0
                val dynamicPulseAngle = timeFactor * 50
                while (a2 < 360) {
                    val angleRad = (ringAngle + a2 + dynamicPulseAngle) * RAD_CONVERT
                    val x = centerX + ringRadius * cos(angleRad)
                    val y = centerY + ringRadius * sin(angleRad)
                    canvas.drawCircle(x, y, 3f + vPercent * 5f, paint)
                    a2 += 30
                }
                paint.style = Paint.Style.STROKE
            }
        }

        // ========== 2. 粒子系统 ==========
        val spawnChance = 0.08f + vPercent * 0.7f
        for (i in 0 until particleCount) {
            val p = particles[i]
            if (!p.active && random.nextFloat() < spawnChance) {
                p.active = true
                p.angle = random.nextFloat() * 2 * PI.toFloat()
                p.radius = baseRadius
                p.life = 0f
                p.speed = 0.6f + vPercent * 3f + random.nextFloat()
            }

            if (p.active) {
                p.radius += p.speed * (0.4f + vPercent)
                p.life += 0.04f

                if (p.radius > maxRadius || p.life > 1.0f) {
                    p.active = false
                    p.radius = 0f
                    p.life = 0f
                    continue
                }

                val cosP = cos(p.angle)
                val sinP = sin(p.angle)
                val x = centerX + p.radius * cosP
                val y = centerY + p.radius * sinP

                val alpha = (200 * (1 - p.life) * (0.2f + 0.8f * vPercent)).toInt()
                particlePaint.color = Color.argb(alpha, r, g, b)

                val particleSize = p.size * (1 - p.life * 0.6f)
                canvas.drawCircle(x, y, particleSize, particlePaint)

                if (p.life < 0.7f) {
                    val trailX = centerX + (p.radius - p.speed * 2.5f) * cosP
                    val trailY = centerY + (p.radius - p.speed * 2.5f) * sinP
                    particlePaint.alpha = alpha shr 1 // 采用位移运算代替整除，加速单字节计算
                    canvas.drawCircle(trailX, trailY, particleSize * 0.5f, particlePaint)
                }
            }
        }

        // ========== 3. 中心多边形核心 ==========
        val coreRotateSpeed = 0.2f + vPercent * 0.8f
        coreRotation += coreRotateSpeed
        if (coreRotation > 360f) coreRotation -= 360f

        paint.style = Paint.Style.FILL

        // 享元重构：清空复用 Path 容器，杜绝在绘制热点路径时产生垃圾碎片
        polygonPath.rewind()
        val expansion = 1f + vPercent * 0.1f
        val dynamicRadius = baseRadius * expansion

        for (i in 0 until polygonSides) {
            val angleRad = (coreRotation + i * 360f / polygonSides) * RAD_CONVERT
            val x = centerX + dynamicRadius * cos(angleRad)
            val y = centerY + dynamicRadius * sin(angleRad)
            if (i == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
        }
        polygonPath.close()

        // 🌟 渐变懒加载：只有在半径发生质变或颜色变化时重新 new 渐变，节省 99% 的线性内存分配
        val targetGradientRadius = baseRadius * 1.3f
        if (dirtyGradient || coreGradient == null || lastGradientRadius != targetGradientRadius) {
            coreGradient = RadialGradient(
                centerX, centerY, targetGradientRadius,
                intArrayOf(_lineColor, Color.argb(220, r, g, b), Color.argb(100, r, g, b), Color.TRANSPARENT),
                floatArrayOf(0f, 0.4f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            lastGradientRadius = targetGradientRadius
            dirtyGradient = false
        }
        paint.shader = coreGradient
        canvas.drawPath(polygonPath, paint)

        // 内层高光层复用
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        paint.alpha = (80 * (0.3f + vPercent * 0.5f)).toInt()

        innerPath.rewind()
        val innerScale = 0.5f + vPercent * 0.1f
        val innerStartAngle = coreRotation + 15f
        val innerDynamicRadius = baseRadius * innerScale

        for (i in 0 until polygonSides) {
            val angleRad = (innerStartAngle + i * 360f / polygonSides) * RAD_CONVERT
            val x = centerX + innerDynamicRadius * cos(angleRad)
            val y = centerY + innerDynamicRadius * sin(angleRad)
            if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
        }
        innerPath.close()
        canvas.drawPath(innerPath, paint)

        // 中心亮点
        paint.alpha = 200
        canvas.drawCircle(centerX, centerY, baseRadius * 0.12f, paint)
        paint.alpha = 255
    }

    /**
     * 极致省电静态场景：剥离多边形和微积分粒子，实现静态绝对锁存
     */
    private fun drawStaticScene(canvas: Canvas, timeFactor: Float) {
        val r = Color.red(_lineColor)
        val g = Color.green(_lineColor)
        val b = Color.blue(_lineColor)

        paint.style = Paint.Style.FILL
        paint.shader = null
        paint.color = Color.argb(100, r, g, b)

        // 画一个静态的基础内圈核心作视觉兜底
        polygonPath.rewind()
        for (i in 0 until polygonSides) {
            val angleRad = (coreRotation + i * 360f / polygonSides) * RAD_CONVERT
            val x = centerX + baseRadius * cos(angleRad)
            val y = centerY + baseRadius * sin(angleRad)
            if (i == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
        }
        polygonPath.close()
        canvas.drawPath(polygonPath, paint)

        paint.color = Color.WHITE
        paint.alpha = 50
        canvas.drawCircle(centerX, centerY, baseRadius * 0.12f, paint)
        paint.alpha = 255
    }

    private fun softerChangeVolume() {
        val localPerVolume = perVolume
        val localTargetVolume = targetVolume
        when {
            volume < localTargetVolume - localPerVolume -> volume += localPerVolume
            volume > localTargetVolume + localPerVolume -> volume = max(0f, volume - localPerVolume) // 🌟 纠正阻尼底限至0f
            else -> volume = localTargetVolume.toFloat()
        }
    }

    // ========== IWaveAnimView 接口实现 ==========
    override val view: View get() = this

    override fun setWaveformColor(color: Int) {
        this.lineColor = color
    }

    override fun setVolume(volume: Int) {
        val inputVolume = volume.coerceIn(0, 100)
        if (abs((targetVolume - inputVolume).toFloat()) > perVolume || inputVolume > 0) {
            targetVolume = inputVolume
            checkVolumeValue()

            // 收到音量大于0，立刻解开同步锁，唤醒后台渲染线程
            if (inputVolume > 0 && isEngineSleeping) {
                synchronized(renderLock) {
                    isEngineSleeping = false
                    renderLock.notifyAll()
                }
            }
        }
    }

    override fun stopAnim() {
        super.stopAnim()
        clearDraw()
        synchronized(renderLock) {
            isEngineSleeping = false
            renderLock.notifyAll()
        }
    }

    override fun release() {
        stopAnim()
        particles.clear()
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
            synchronized(renderLock) {
                isEngineSleeping = false
                renderLock.notifyAll()
            }
            startAnim()
        }
    }

    fun clearDraw() {
        var canvas: Canvas? = null
        try {
            canvas = holder?.lockCanvas()
            if (canvas != null) {
                if (isTransparentMode) {
                    canvas.drawColor(_backgroundColor, PorterDuff.Mode.CLEAR)
                } else {
                    canvas.drawColor(_backgroundColor)
                }
            }
        } catch (ignored: Exception) {
        } finally {
            canvas?.let { holder?.unlockCanvasAndPost(it) }
        }
    }

    fun setMoveSpeed(moveSpeed: Float) { offsetSpeed = moveSpeed }
    fun setSensibility(sensibility: Int) {
        this.sensibility = sensibility
        checkSensibilityValue()
    }

    fun setPolygonSides(sides: Int) {
        polygonSides = sides.coerceIn(6, 12)
    }

    private fun checkVolumeValue() {
        if (targetVolume > 100) targetVolume = 100
        if (targetVolume < 0) targetVolume = 0
    }

    private fun checkSensibilityValue() {
        if (sensibility > 10) sensibility = 10
        if (sensibility < 1) sensibility = 1
    }

    private data class Particle(
        var angle: Float = 0f,
        var radius: Float = 0f,
        var speed: Float = 0f,
        var size: Float = 0f,
        var life: Float = 0f,
        var active: Boolean = false
    )
}