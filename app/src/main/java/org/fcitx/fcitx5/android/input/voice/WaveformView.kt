package org.fcitx.fcitx5.android.input.voice

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import org.fcitx.fcitx5.android.data.LayoutData
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.ui.widget.IWaveAnimView
import org.fcitx.fcitx5.android.ui.widget.ParticleWaveView
import org.fcitx.fcitx5.android.ui.widget.SphereRippleView
import kotlin.math.ln

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val layoutPrefs = AppPrefs.getInstance().layout

    companion object {
        private const val AMPLITUDE_GAIN = 1.6
        private const val AMPLITUDE_LOG_K = 18.0
    }

    private var isActive = false
    private var currentStyle: WaveStyle? = null
    private var currentAnimView: IWaveAnimView? = null

    // 子 View 享元池：彻底卡死实例化源头，全生命周期内有且仅有一次绘制添加
    private val animViewCache = HashMap<WaveStyle, IWaveAnimView>()

    @ColorInt
    private var waveformColor: Int = Color.WHITE

    /**
     * 享元模式：动态切样式，杜绝 addView 导致的 requestLayout 链式性能惩罚
     */
    fun setUIStyle(style: WaveStyle) {
        if (currentStyle == style && currentAnimView != null) return

        val wasRunning = isActive
        if (wasRunning) stop()

        // 隐藏当前活动的 View，腾出视觉空间
        currentAnimView?.view?.visibility = View.GONE
        currentStyle = style

        // 检查缓存池
        var targetAnimView = animViewCache[style]

        if (targetAnimView == null) {
            targetAnimView = when (style) {
                WaveStyle.SPHERE_RIPPLE -> {
                    SphereRippleView(context).apply {
                        backGroundColor = Color.TRANSPARENT
                        setSensibility(15)
                        setMoveSpeed(160f)
                    }
                }
                WaveStyle.PARTICLE_WAVE -> {
                    ParticleWaveView(context).apply {
                        backGroundColor = Color.TRANSPARENT
                        setPolygonSides(10)
                        setSensibility(15)
                        setMoveSpeed(160f)
                    }
                }
            }
            targetAnimView.setWaveformColor(waveformColor)
            animViewCache[style] = targetAnimView

            // 只有在首次创建时，才真正触发底层的测量与绘制挂载
            addView(targetAnimView.view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        } else {
            targetAnimView.setWaveformColor(waveformColor)
        }

        targetAnimView.view.visibility = View.VISIBLE
        currentAnimView = targetAnimView

        if (wasRunning) start()
    }

    fun setWaveformColor(@ColorInt color: Int) {
        this.waveformColor = color
        animViewCache.values.forEach { it.setWaveformColor(color) }
    }

    fun updateAmplitude(amplitude: Float) {
        if (!isActive) return
        val volume = amplitudeToVolume(amplitude)
        currentAnimView?.setVolume(volume)
    }

    private fun amplitudeToVolume(amplitude: Float): Int {
        val a = (amplitude.coerceIn(0f, 1f).toDouble() * AMPLITUDE_GAIN).coerceIn(0.0, 1.0)
        val mapped = ln(1.0 + AMPLITUDE_LOG_K * a) / ln(1.0 + AMPLITUDE_LOG_K)
        return (mapped * 100.0).toInt().coerceIn(0, 100)
    }

    fun start() {
        if (isActive) return
        isActive = true

        val targetStyle = if (layoutPrefs.voiceKeyboardStyle.getValue() == LayoutData.VoiceKeyboardStyle.ParticleRing) {
            WaveStyle.PARTICLE_WAVE
        } else {
            WaveStyle.SPHERE_RIPPLE
        }

        setUIStyle(targetStyle)
        runCatching { currentAnimView?.startAnim() }
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        runCatching { currentAnimView?.stopAnim() }
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        runCatching { currentAnimView?.onWindowFocusChanged(hasWindowFocus) }
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (changedView == this && visibility != View.VISIBLE && isActive) {
            stop()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stop()
        animViewCache.values.forEach { it.release() }
        animViewCache.clear()
        currentAnimView = null
    }
}