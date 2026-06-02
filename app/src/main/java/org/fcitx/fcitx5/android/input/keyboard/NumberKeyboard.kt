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
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.popup.PopupAction
import splitties.views.imageResource
import kotlin.collections.asSequence
import kotlin.collections.orEmpty

@SuppressLint("ViewConstructor")
class NumberKeyboard(
    context: Context,
    theme: Theme,
) : SidePanelKeyboard(
    context, theme, 3, 4, SideLayout, Layout
) {
    private val fcitx = FcitxDaemon.connect(javaClass.name)

    init {
        updateSideBarItems(SideLayoutItems)
    }

    companion object {
        const val Name = "Number"

        val SideLayoutItems = listOf(
            CommitKey("+", textSize = 18f),
            CommitKey("-", textSize = 18f),
            CommitKey("*", textSize = 18f),
            CommitKey("/", textSize = 18f),
            CommitKey("=", textSize = 18f),
            CommitKey(":", textSize = 18f),
            CommitKey("...", textSize = 18f),
            CommitKey("?", textSize = 18f),
            CommitKey("!", textSize = 18f),
        )

        val SideLayout: KeyDef = ColumnKey(
            percentWidth = 0.15f,
            variant = Variant.Alternative,
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                PlaceHolderKey(),
                NumPadKey("1", 0xffb1, 26f, 0f),
                NumPadKey("2", 0xffb2, 26f, 0f),
                NumPadKey("3", 0xffb3, 26f, 0f),
                BackspaceKey(),
            ), listOf(
                PlaceHolderKey(),
                NumPadKey("4", 0xffb4, 26f, 0f),
                NumPadKey("5", 0xffb5, 26f, 0f),
                NumPadKey("6", 0xffb6, 26f, 0f),
                MiniSpaceKey()
            ), listOf(
                PlaceHolderKey(),
                NumPadKey("7", 0xffb7, 26f, 0f),
                NumPadKey("8", 0xffb8, 26f, 0f),
                NumPadKey("9", 0xffb9, 26f, 0f),
                CommitKey("@", textSize = 20f, percentWidth = 0.15f, variant = Variant.Alternative),
            ), listOf(
                LayoutSwitchKey("Abc", "", textSize = 15f, percentWidth = 0.15f),
                NumPadKey(",", 0xffac, 18f, 0.1f, KeyDef.Appearance.Variant.Alternative),
                CommitKey(
                    "^",
                    border = Border.Default,
                    percentWidth = 0.13333f,
                    textSize = 18f,
                    variant = Variant.Alternative
                ),
                NumPadKey("0", 0xffb0, 26f, 0f),
                CommitKey(
                    "~",
                    border = Border.Default,
                    percentWidth = 0.13333f,
                    textSize = 18f,
                    variant = Variant.Alternative
                ),
                CommaKey(percentWidth = 0.1f, variant = Variant.Alternative),
                ReturnKey()
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

    override fun onAttach() {
        updateSideBarItems(SideLayoutItems)
        super.onAttach()
    }

    override fun shouldUpdateSidePanelWhenFcitxEvent(): Boolean {
        return false
    }
}