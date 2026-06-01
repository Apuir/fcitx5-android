/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.transition.Slide
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.CapabilityFlags
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.broadcast.ReturnKeyDrawableComponent
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.inputMethodService
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.picker.PickerWindow
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.input.voice.WaveformView
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.fcitx.fcitx5.android.link.SherpaSpeechClient
import org.fcitx.fcitx5.android.link.VoiceOverlayUiBridge
import org.mechdancer.dependency.manager.must
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import timber.log.Timber

class KeyboardWindow : InputWindow.SimpleInputWindow<KeyboardWindow>(), EssentialWindow,
    InputBroadcastReceiver {

    private val service by manager.inputMethodService()
    private val fcitx by manager.fcitx()
    private val theme by manager.theme()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val windowManager: InputWindowManager by manager.must()
    private val popup: PopupComponent by manager.must()
    private val bar: KawaiiBarComponent by manager.must()
    private val returnKeyDrawable: ReturnKeyDrawableComponent by manager.must()

    companion object : EssentialWindow.Key {
        val preferKeyboardMap = mapOf(
            "T9" to T9TextKeyboard.Name, "T26" to TextKeyboard.Name
        )

        var voiceRecordingMode: VoiceRecordingMode = VoiceRecordingMode.None

        enum class VoiceRecordingMode {
            None, //没有在录音
            Normal, //普通模式（overlay模式）
            KawaiiBar //工具栏模式
        }
    }

    override val key: EssentialWindow.Key
        get() = KeyboardWindow

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between picker
        lastWindow !is PickerWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) =
        super.exitAnimation(nextWindow).takeIf {
            // disable animation switching between picker
            nextWindow !is PickerWindow
        }

    private lateinit var keyboardView: FrameLayout
    private var voiceOverlay: View? = null
    private var voiceWave: WaveformView? = null

    private val keyboards: HashMap<String, BaseKeyboard> by lazy {
        hashMapOf(
            TextKeyboard.Name to TextKeyboard(context, theme),
            NumberKeyboard.Name to NumberKeyboard(context, theme),
            T9TextKeyboard.Name to T9TextKeyboard(context, theme),
            MixNumberKeyboard.Name to MixNumberKeyboard(context,theme)
        )
    }
    private var currentKeyboardName = ""
    private var lastSymbolType: String by AppPrefs.getInstance().internal.lastSymbolLayout

    private val currentKeyboard: BaseKeyboard? get() = keyboards[currentKeyboardName]

    private val keyActionListener = KeyActionListener { it, source ->
        //音频中，额外输入的效果
        when (voiceRecordingMode) {
            VoiceRecordingMode.None -> {}
            VoiceRecordingMode.KawaiiBar -> stopKawaiiBarVoiceRecording()
            VoiceRecordingMode.Normal -> stopNormalVoiceRecording()
        }
        if (it is KeyAction.LayoutSwitchAction) {
            switchLayout(it.act)
        } else {
            commonKeyActionListener.listener.onKeyAction(it, source)
        }
    }

    private val popupActionListener: PopupActionListener by lazy {
        popup.listener
    }

    // This will be called EXACTLY ONCE
    override fun onCreateView(): View {
        keyboardView = context.frameLayout(R.id.keyboard_view)
        attachLayout(TextKeyboard.Name)
        return keyboardView
    }

    private fun detachCurrentLayout() {
        currentKeyboard?.also {
            it.onDetach()
            keyboardView.removeView(it)
            it.keyActionListener = null
            it.popupActionListener = null
        }
    }

    private fun attachLayout(target: String) {
        currentKeyboardName = target
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            keyboardView.apply { add(it, lParams(matchParent, matchParent)) }
            it.onAttach()
            it.onReturnDrawableUpdate(returnKeyDrawable.resourceId)
            it.onInputMethodUpdate(fcitx.runImmediately { inputMethodEntryCached })
        }
    }

    fun switchLayout(to: String, remember: Boolean = true) {
        val target = to.ifEmpty { lastSymbolType }
        ContextCompat.getMainExecutor(service).execute {
            if (keyboards.containsKey(target)) {
                if (remember && target != TextKeyboard.Name) {
                    lastSymbolType = target
                }
                if (target == currentKeyboardName) return@execute
                detachCurrentLayout()
                attachLayout(target)
                if (windowManager.isAttached(this)) {
                    notifyBarLayoutChanged()
                }
            } else {
                if (remember) {
                    lastSymbolType = PickerWindow.Key.Symbol.name
                }
                windowManager.attachWindow(PickerWindow.Key.Symbol)
            }
        }
    }

    override fun onStartInput(info: EditorInfo, capFlags: CapabilityFlags) {
        val targetLayout = when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER -> NumberKeyboard.Name
            InputType.TYPE_CLASS_PHONE -> NumberKeyboard.Name
            else -> TextKeyboard.Name
        }
        when(targetLayout){
            NumberKeyboard.Name -> switchLayout(targetLayout, remember = false)
            else -> autoSwitchLayout(targetLayout)
        }
    }

    override fun onImeUpdate(ime: InputMethodEntry) {
        currentKeyboard?.onInputMethodUpdate(ime)
        when(currentKeyboardName){
            NumberKeyboard.Name ->{}
            else -> autoSwitchLayout(TextKeyboard.Name)
        }
    }

    override fun onPunctuationUpdate(mapping: Map<String, String>) {
        currentKeyboard?.onPunctuationUpdate(mapping)
    }

    override fun onReturnKeyDrawableUpdate(resourceId: Int) {
        currentKeyboard?.onReturnDrawableUpdate(resourceId)
    }

    override fun onAttached() {
        currentKeyboard?.let {
            it.keyActionListener = keyActionListener
            it.popupActionListener = popupActionListener
            it.onAttach()
        }
        notifyBarLayoutChanged()
    }

    override fun onDetached() {
        // 清理语音覆盖层，避免窗口切换后残留
        stopNormalVoiceRecording()
        currentKeyboard?.let {
            it.onDetach()
            it.keyActionListener = null
            it.popupActionListener = null
        }
        popup.dismissAll()
    }

    // Call this when
    // 1) the keyboard window was newly attached
    // 2) currently keyboard window is attached and switchLayout was used
    private fun notifyBarLayoutChanged() {
        bar.onKeyboardLayoutSwitched(currentKeyboardName == NumberKeyboard.Name)
    }

    // 主UI支持不同引擎的自主逻辑切换
    private fun autoSwitchLayout(fallback: String){
        service.postFcitxJob {
            val config = getImConfig(currentIme().uniqueName)
            val preferLayout = config.subItems
                ?.asSequence()
                ?.flatMap { it.subItems.orEmpty().asSequence() }
                ?.firstOrNull { it.name == "PreferKeyboard" }
                ?.value
                ?.let { preferKeyboardMap[it] }
            switchLayout(preferLayout ?: fallback, remember = false)
        }
    }

    fun startKawaiiBarVoiceRecording() {
        if (voiceRecordingMode != VoiceRecordingMode.None) return
        SherpaSpeechClient.startHoldSession(service)
        voiceRecordingMode = VoiceRecordingMode.KawaiiBar
    }

    fun stopKawaiiBarVoiceRecording() {
        service.updateBarIsVoiceRecording(false)
        service.currentInputConnection.finishComposingText()
        SherpaSpeechClient.stopHoldSession()
        voiceRecordingMode = VoiceRecordingMode.None
    }

    fun startKawaiiBarVoiceOverlay(){
        service.updateBarIsVoiceRecording(true)
    }

    // =====================================================================
    // 🌟 优化核心：将语音覆盖层与波形 View 变成长驻成员，确保全生命周期【只实例化和绘制一次】
    // =====================================================================
    private var voiceOverlayLayout: FrameLayout? = null
    private var cachedVoiceWave: WaveformView? = null
    private var isVoiceOverlayShowing = false

    /**
     * 显示“语音输入占位”覆盖层（零内存分配、只会绘制一次的终极优化版）
     */
    fun startNormalVoiceRecording() {
        Timber.d("kkkk")
        if (voiceRecordingMode != VoiceRecordingMode.None) return
        voiceRecordingMode = VoiceRecordingMode.Normal

        // 1. 获取或懒加载初始化语音覆盖层（有且仅执行一次）
        var overlay = voiceOverlayLayout
        if (overlay == null) {
            val bgColor = when (val t = theme) {
                is org.fcitx.fcitx5.android.data.theme.Theme.Builtin -> t.keyboardColor
                else -> theme.backgroundColor
            }

            overlay = FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                setBackgroundColor(bgColor)
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                // 首次创建时先隐藏，且扔到 Y 轴屏幕下方，为自底向上动画做基准准备
                visibility = View.INVISIBLE
            }

            val wave = WaveformView(context).apply {
                val candidateColors = listOf(
                    theme.genericActiveForegroundColor,
                    theme.accentKeyBackgroundColor,
                    theme.keyTextColor
                )
                val lineColor = candidateColors.firstOrNull {
                    ColorUtils.calculateContrast(it, bgColor) >= 2.5
                } ?: theme.genericActiveForegroundColor
                setWaveformColor(lineColor)
                visibility = View.INVISIBLE
            }

            overlay.addView(wave, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

            // 🌟 整个生命周期内，有且仅有一次 addView 挂载操作，彻底免除后续的测绘惩罚
            keyboardView.addView(overlay)

            voiceOverlayLayout = overlay
            cachedVoiceWave = wave
        }
    }

    /**
     * 激活覆盖层内的声波动画
     */
    fun startVoiceOverlay() {
        val wave = cachedVoiceWave ?: return
        val overlay = voiceOverlayLayout ?: return

        val currentBgColor = when (val t = theme) {
            is org.fcitx.fcitx5.android.data.theme.Theme.Builtin -> t.keyboardColor
            else -> theme.backgroundColor
        }
        val alpha = 200
        overlay.setBackgroundColor(Color.argb(alpha, Color.red(currentBgColor), Color.green(
            currentBgColor
        ), Color.blue(currentBgColor)))

        overlay.animate()
            .alpha(1f)
            .setDuration(10)  // 淡入动画时长 10ms
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .setListener(null)
            .start()

        overlay.bringToFront()
        overlay.visibility = View.VISIBLE
        wave.visibility = View.VISIBLE
        wave.start()
    }

    fun startVoiceHoldSession() {
        SherpaSpeechClient.startHoldSession(service)
    }

    fun getVoiceRecodingMode(): VoiceRecordingMode{
        return voiceRecordingMode
    }

    /**
     * 隐藏“语音输入占位”覆盖层（不释放对象，仅隐藏）
     */
    fun stopNormalVoiceRecording() {
        if (voiceRecordingMode != VoiceRecordingMode.Normal) return

        val overlay = voiceOverlayLayout ?: return
        overlay.animate()
            .setDuration(100)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .setListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 动画结束时，把容器隐藏起来，此时不占用任何 GPU 渲染带宽
                    if (!isVoiceOverlayShowing) {
                        overlay.visibility = View.INVISIBLE
                    }
                }
            })
            .start()

        VoiceOverlayUiBridge.clear()
        SherpaSpeechClient.stopHoldSession()

        // 优雅停止前面改造过的低功耗波形工作线程
        try { cachedVoiceWave?.stop() } catch (_: Throwable) {}
        cachedVoiceWave?.visibility = View.INVISIBLE
        voiceOverlayLayout?.visibility = View.INVISIBLE
        voiceRecordingMode = VoiceRecordingMode.None
    }

    /**
     * 实时音量更新桥接
     */
    fun updateVoiceOverlayAmplitude(amplitude: Float) {
        cachedVoiceWave?.updateAmplitude(amplitude)
    }
}