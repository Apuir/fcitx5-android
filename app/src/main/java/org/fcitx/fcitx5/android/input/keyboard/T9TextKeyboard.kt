/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.popup.PopupAction
import timber.log.Timber

@SuppressLint("ViewConstructor")
class T9TextKeyboard(
    context: Context, theme: Theme
) : ColumnKeyboard(
    context, theme, SideLayoutColumnNum, SideLayoutColumnShowNum, SideLayout, Layout
) {

    companion object {
        const val Name = "T9Text"

        //占用3行
        const val SideLayoutColumnNum = 3

        //占用3行 容器里显示4行
        const val SideLayoutColumnShowNum = 4

        val SideLayout: KeyDef = ColumnKey(
            percentWidth = 0.15f, variant = Variant.Alternative, children = listOf(
                CommitKey("，"),
                CommitKey("。"),
                CommitKey("？"),
                CommitKey("！"),
            )
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                PlaceHolderKey(),
                MixAlphabetKey("分词", "1"),
                MixAlphabetKey("ABC", "2"),
                MixAlphabetKey("DEF", "3"),
                BackspaceKey(percentWidth = 0.15f),
            ), listOf(
                PlaceHolderKey(),
                MixAlphabetKey("GHI", "4"),
                MixAlphabetKey("JKL", "5"),
                MixAlphabetKey("MNO", "6"),
                ClearKey(displayText = "清空", percentWidth = 0.15f),
            ), listOf(
                PlaceHolderKey(),
                MixAlphabetKey("PQRS", "7"),
                MixAlphabetKey("TUV", "8"),
                MixAlphabetKey("WXYZ", "9"),
                VoiceKey(),
            ), listOf(
                LayoutSwitchKey("?123", MixNumberKeyboard.Name, percentWidth = 0.15f, textSize = 15f),
                LanguageKey(percentWidth = 0.15f, variant = Variant.Alternative),
                SpaceKey(),
                CommaKey(".",0.15f, Variant.Alternative),
                ReturnKey(percentWidth = 0.15f)
            )
        )
    }

    val space: TextKeyView by lazy { findViewById(R.id.button_space) }

    override fun onInputMethodUpdate(ime: InputMethodEntry) {
        space.mainText.text = buildString {
            append(ime.displayName)
            ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" [$it] ") }
        }
    }

    override fun onAction(action: KeyAction, source: KeyActionListener.Source) {
        val newAction = when {
            //弹出键盘的数字直接上屏,而不是作为普通键
            action is KeyAction.FcitxKeyAction && source == KeyActionListener.Source.Popup -> {
                val act = action.act
                if (act.length == 1 && act.all(Char::isDigit)) {
                    KeyAction.CommitAction(act)
                } else {
                    action
                }
            }
            else -> action
        }
        super.onAction(newAction, source)
    }

    override fun onPopupAction(action: PopupAction) {
        val newAction = when (action) {
            is PopupAction.ShowKeyboardAction -> {
                val label = action.keyboard.label
                action.copy(
                    keyboard = KeyDef.Popup.Keyboard(
                        "T9-$label"
                    )
                )
            }
            else -> action
        }
        super.onPopupAction(newAction)
    }

}