package org.fcitx.fcitx5.android.ui.widget

import android.view.View
import androidx.annotation.ColorInt

/**
 * 语音动画视图的统一调度接口
 * 通过默认实现 [view] 属性，实现自定义 View 与容器的无缝解耦
 */
interface IWaveAnimView {

    /**
     * 获取当前 View 实例，供容器 FrameLayout 直接执行 addView
     */
    val view: View
        get() = this as View

    /**
     * 设置声波波形的主色调
     */
    fun setWaveformColor(@ColorInt color: Int)

    /**
     * 更新实时音量 (0 ~ 100)
     */
    fun setVolume(volume: Int)

    /**
     * 启动动画引擎线程
     */
    fun startAnim()

    /**
     * 停止动画引擎线程
     */
    fun stopAnim()

    /**
     * 响应窗口焦点变化（SurfaceView 生命周期桥接）
     */
    fun onWindowFocusChanged(hasWindowFocus: Boolean)

    /**
     * 释放底层线程与图形资源
     */
    fun release()
}