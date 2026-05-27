/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import kotlin.collections.asSequence
import kotlin.collections.orEmpty


@SuppressLint("ViewConstructor")
class MixNumberKeyboard(
    context: Context,
    theme: Theme,
) : BaseKeyboard(context, theme, Layout) {

    private val fcitx = FcitxDaemon.connect(javaClass.name)

    companion object {
        const val Name = "MixNumber"

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                CommitKey("%",  percentWidth = 0.125f),
                CommitKey("!",  percentWidth = 0.1f),
                CommitKey("?",  percentWidth = 0.1f),
                CommitKey("+",  percentWidth = 0.1f),
                CommitKey("1",  percentWidth = 0.15f, variant = Variant.Normal),
                CommitKey("2",  percentWidth = 0.15f, variant = Variant.Normal),
                CommitKey("3",  percentWidth = 0.15f, variant = Variant.Normal),
                BackspaceKey(percentWidth = 0.125f)
            ), listOf(
                CommitKey("&",  percentWidth = 0.125f),
                CommitKey("(",  percentWidth = 0.1f),
                CommitKey(")",  percentWidth = 0.1f),
                CommitKey("-",  percentWidth = 0.1f),
                CommitKey("4",  percentWidth = 0.15f, variant = Variant.Normal),
                CommitKey("5",  percentWidth = 0.15f, variant = Variant.Normal),
                CommitKey("6",  percentWidth = 0.15f, variant = Variant.Normal),
                MiniSpaceKey(percentWidth = 0.125f),
            ), listOf(
                CommitKey("~",  percentWidth = 0.125f),
                CommitKey(":",  percentWidth = 0.1f),
                CommitKey(";",  percentWidth = 0.1f),
                CommitKey("*",  percentWidth = 0.1f),
                CommitKey("7",  percentWidth = 0.15f, variant = Variant.Normal),
                CommitKey("8",  percentWidth = 0.15f, variant = Variant.Normal),
                CommitKey("9",  percentWidth = 0.15f, variant = Variant.Normal),
                CommitKey(
                    "=",  percentWidth = 0.125f, variant = Variant.Alternative
                ),
            ), listOf(
                LayoutSwitchKey("Abc", "", percentWidth = 0.125f, textSize = 15f),
                CommitKey("<",  percentWidth = 0.1f),
                CommitKey(">",  percentWidth = 0.1f),
                CommitKey("/",  percentWidth = 0.1f),
                CommitKey(
                    ",",  percentWidth = 0.15f, variant = Variant.Alternative
                ),
                CommitKey("0",  percentWidth = 0.15f, variant = Variant.Normal),
                CommaKey(percentWidth = 0.15f, variant = Variant.Alternative),
                ReturnKey(percentWidth = 0.125f)
            )
        )
    }

    val backspace: ImageKeyView by lazy { findViewById(R.id.button_backspace) }
    val space: TextKeyView by lazy { findViewById(R.id.button_mini_space) }
    val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

    override fun onReturnDrawableUpdate(returnDrawable: Int) {
        `return`.img.imageResource = returnDrawable
    }

    @SuppressLint("MissingSuperCall")
    override fun onPopupAction(action: PopupAction) {
        // leave empty on purpose to disable popup in NumberKeyboard
        super.onPopupAction(action)
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        when (action) {
            is KeyAction.LayoutSwitchAction -> {
                //如果为空，那么根据输入法当前的IME信息自动跳转
                if (action.act == "") {
                    fcitx.lifecycleScope.launch {
                        fcitx.runIfReady {
                            val config = getImConfig(currentIme().uniqueName)
                            val preferLayout = config.subItems?.asSequence()
                                ?.flatMap { it.subItems.orEmpty().asSequence() }
                                ?.firstOrNull { it.name == "PreferKeyboard" }?.value?.let { KeyboardWindow.preferKeyboardMap[it] }
                            super.onAction(
                                KeyAction.LayoutSwitchAction(
                                    preferLayout ?: TextKeyboard.Name
                                ), source
                            )
                        }
                    }
                }
            }
            else -> super.onAction(action, source)
        }
    }
}