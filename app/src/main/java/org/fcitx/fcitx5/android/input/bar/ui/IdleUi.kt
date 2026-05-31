/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2025 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.bar.ui

import android.content.Context
import android.transition.Slide
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.Space
import android.widget.ViewAnimator
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.bar.KawaiiBarComponent
import org.fcitx.fcitx5.android.input.bar.ui.idle.ButtonsBarUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.ClipboardSuggestionUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.InlineSuggestionsUi
import org.fcitx.fcitx5.android.input.bar.ui.idle.NumberRow
import org.fcitx.fcitx5.android.input.bar.ui.widget.RecordingWaveView
import org.fcitx.fcitx5.android.input.keyboard.CommonKeyActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.after
import splitties.views.dsl.constraintlayout.before
import splitties.views.dsl.constraintlayout.centerVertically
import splitties.views.dsl.constraintlayout.constraintLayout
import splitties.views.dsl.constraintlayout.endOfParent
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.startOfParent
import splitties.views.dsl.core.Ui
import splitties.views.dsl.core.add
import splitties.views.dsl.core.frameLayout
import splitties.views.dsl.core.lParams
import splitties.views.dsl.core.matchParent
import splitties.views.imageResource
import timber.log.Timber

class IdleUi(
    override val ctx: Context,
    private val theme: Theme,
    private val popup: PopupComponent,
    private val commonKeyActionListener: CommonKeyActionListener
) : Ui {

    enum class State {
        Empty, Toolbar, Clipboard, NumberRow, InlineSuggestion, Recording
    }

    var currentState = State.Empty
        private set

    private val disableAnimation by AppPrefs.getInstance().advanced.disableAnimation

    private var inPrivate = false

    private val translateDirection by lazy {
        if (ctx.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) 1f else -1f
    }

    private val menuButtonRotation
        get() = when {
            inPrivate -> 0f
            currentState == State.Toolbar -> 90f * translateDirection
            else -> -90f * translateDirection
        }

    val menuButton = ToolButton(ctx, R.drawable.ic_baseline_expand_more_24, theme).apply {
        rotation = menuButtonRotation
    }

    val hideKeyboardButton = ToolButton(ctx, R.drawable.ic_baseline_arrow_drop_down_24, theme)

    val emptyBar = Space(ctx)

    val buttonsUi = ButtonsBarUi(ctx, theme)

    val clipboardUi = ClipboardSuggestionUi(ctx, theme)

    val numberRow = NumberRow(ctx, theme).apply {
        visibility = View.GONE
    }

    val inlineSuggestionsBar = InlineSuggestionsUi(ctx)

    // ==========================================
    // 组装内嵌精致三点呼吸 View 的录音栏容器
    // ==========================================
    val recordingBar = FrameLayout(ctx).apply {
        val waveView = RecordingWaveView(ctx, theme)
        add(waveView, lParams(matchParent, matchParent))

        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (disableAnimation) return
                waveView.setThemeColor(theme)
                waveView.startAnimation()
            }

            override fun onViewDetachedFromWindow(v: View) {
                waveView.stopAnimation()
            }
        })
    }

    private val animator = ViewAnimator(ctx).apply {
        add(emptyBar, lParams(matchParent, matchParent))                  // index 0
        add(buttonsUi.root, lParams(matchParent, matchParent))             // index 1
        add(clipboardUi.root, lParams(matchParent, matchParent))           // index 2
        add(inlineSuggestionsBar.root, lParams(matchParent, matchParent))   // index 3
        add(recordingBar, lParams(matchParent, matchParent))               // index 4
    }

    private val inAnimation by lazy {
        AnimationSet(true).apply {
            duration = 200L
            addAnimation(AlphaAnimation(0f, 1f))
            addAnimation(TranslateAnimation(2, -0.3f * translateDirection, 2, 0f, 0, 0f, 0, 0f))
        }
    }

    private val outAnimation by lazy {
        AnimationSet(true).apply {
            duration = 200L
            addAnimation(AlphaAnimation(1f, 0f))
            addAnimation(TranslateAnimation(2, 0f, 2, -0.3f * translateDirection, 0, 0f, 0, 0f))
        }
    }

    private val idleBody = constraintLayout {
        val size = dp(KawaiiBarComponent.HEIGHT)
        add(menuButton, lParams(size, size) {
            startOfParent()
            centerVertically()
        })
        add(hideKeyboardButton, lParams(size, size) {
            endOfParent()
            centerVertically()
        })
        add(animator, lParams(matchConstraints, matchParent) {
            after(menuButton)
            before(hideKeyboardButton)
            centerVertically()
        })
    }

    override val root = frameLayout {
        add(idleBody, lParams(matchParent, matchParent))
        add(numberRow, lParams(matchParent, matchParent))
    }

    fun privateMode(activate: Boolean = true) {
        if (activate == inPrivate) return
        inPrivate = activate
        updateMenuButtonIcon()
        updateMenuButtonContentDescription()
        updateMenuButtonRotation(instant = true)
    }

    private fun updateMenuButtonIcon() {
        menuButton.image.imageResource =
            if (inPrivate) R.drawable.ic_view_private
            else R.drawable.ic_baseline_expand_more_24
    }

    private fun updateMenuButtonContentDescription() {
        menuButton.contentDescription = when {
            inPrivate -> ctx.getString(R.string.private_mode)
            currentState == State.Toolbar -> ctx.getString(R.string.hide_toolbar)
            else -> ctx.getString(R.string.expand_toolbar)
        }
    }

    private fun updateMenuButtonRotation(instant: Boolean = false) {
        val targetRotation = menuButtonRotation
        menuButton.apply {
            if (targetRotation == rotation) return
            animate().cancel()
            if (!instant && !disableAnimation) {
                animate().setDuration(200L).rotation(targetRotation)
            } else {
                rotation = targetRotation
            }
        }
    }

    // 保存外部传入的普通键盘状态下的收起回调，以便从录音状态切回时可以正确还原
    private var normalHideKeyboardCallback: View.OnClickListener? = null
    private var isVoiceInputMode = false

    fun setHideKeyboardIsVoiceInput(isVoiceInput: Boolean, callback: View.OnClickListener) {
        isVoiceInputMode = isVoiceInput
        normalHideKeyboardCallback = callback

        // 如果当前正是录音状态，图标更新逻辑接管给 updateState 处理，这里不覆盖取消按钮
        if (currentState == State.Recording) return

        if (isVoiceInput) {
            hideKeyboardButton.setIcon(R.drawable.ic_baseline_keyboard_voice_24)
            hideKeyboardButton.contentDescription = ctx.getString(R.string.switch_to_voice_input)
        } else {
            hideKeyboardButton.setIcon(R.drawable.ic_baseline_arrow_drop_down_24)
            hideKeyboardButton.contentDescription = ctx.getString(R.string.hide_keyboard)
        }
        hideKeyboardButton.setOnClickListener(callback)
    }

    /**
     * 提供给外部 Component（如 KawaiiBarComponent）调用的方法，用于监听用户的取消录音动作
     */
    fun setOnCancelRecordingListener(callback: View.OnClickListener) {
        if (currentState == State.Recording) {
            hideKeyboardButton.setOnClickListener(callback)
        }
    }

    private fun clearAnimation() {
        animator.inAnimation = null
        animator.outAnimation = null
    }

    private fun setAnimation() {
        animator.inAnimation = inAnimation
        animator.outAnimation = outAnimation
    }

    private fun enableSlideTransition(inTarget: View, outTarget: View, inGravity: Int, outGravity: Int) {
        val slideIn = Slide(inGravity).apply { duration = 200L }
        val slideOut = Slide(outGravity).apply { duration = 200L }
        slideIn.addTarget(inTarget)
        slideOut.addTarget(outTarget)
        val set = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(slideIn)
            addTransition(slideOut)
        }
        TransitionManager.beginDelayedTransition(root, set)
    }

    fun updateState(state: State, fromUser: Boolean = false) {
        Timber.d("Switch idle ui to $state")
        if (
            !fromUser ||
            disableAnimation ||
            (state == State.InlineSuggestion || currentState == State.InlineSuggestion) ||
            (state == State.NumberRow || currentState == State.NumberRow) ||
            (state == State.Recording || currentState == State.Recording)
        ) {
            clearAnimation()
        } else {
            setAnimation()
        }

        val previousState = currentState
        currentState = state

        when (state) {
            State.Empty -> animator.displayedChild = 0
            State.Toolbar -> animator.displayedChild = 1
            State.Clipboard -> animator.displayedChild = 2
            State.NumberRow -> {}
            State.InlineSuggestion -> animator.displayedChild = 3
            State.Recording -> animator.displayedChild = 4
        }

        // ==========================================
        // 核心改动：录音状态下动态改变右侧按钮为取消按钮
        // ==========================================
        if (state == State.Recording) {
            // 左边菜单键直接隐去
            menuButton.visibility = View.INVISIBLE
            // 右边键保持可见，但变身为“取消”按钮 (使用标准的关闭/清除图标)
            hideKeyboardButton.visibility = View.VISIBLE
            hideKeyboardButton.setIcon(R.drawable.ic_baseline_close_24)
        } else {
            // 切出录音状态，恢复两边按键的原始可见度
            menuButton.visibility = View.VISIBLE
            hideKeyboardButton.visibility = View.VISIBLE

            // 如果是从录音状态切回来的，需要把右侧按钮的图标与点击事件回滚还原
            if (previousState == State.Recording) {
                setHideKeyboardIsVoiceInput(
                    isVoiceInputMode, normalHideKeyboardCallback ?: View.OnClickListener {})
            }
        }

        if (state == State.NumberRow) {
            numberRow.keyActionListener = commonKeyActionListener.listener
            numberRow.popupActionListener = popup.listener
            if (fromUser && !disableAnimation) {
                enableSlideTransition(numberRow, idleBody, Gravity.END, Gravity.START)
            }
            numberRow.visibility = View.VISIBLE
            idleBody.visibility = View.GONE
        } else if (previousState == State.NumberRow) {
            if (fromUser && !disableAnimation) {
                enableSlideTransition(idleBody, numberRow, Gravity.START, Gravity.END)
            }
            idleBody.visibility = View.VISIBLE
            numberRow.visibility = View.GONE
            numberRow.keyActionListener = null
            numberRow.popupActionListener = null
            popup.dismissAll()
        }
        updateMenuButtonContentDescription()
        updateMenuButtonRotation(instant = !fromUser)
    }
}