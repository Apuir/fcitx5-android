/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.Keep
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.popup.PopupAction

@SuppressLint("ViewConstructor")
class T9TextKeyboard(
    context: Context, theme: Theme
) : SidePanelKeyboard(
    context, theme, 3, 4, SideLayout, Layout
) {
    private val layoutPrefs = AppPrefs.getInstance().layout

    private val sidebarSymbolsKey = layoutPrefs.sidebarSymbols

    private val voiceInputBtnReplaced = layoutPrefs.replaceVoiceBtn

    private val voiceInputBtn: ImageKeyView by lazy { findViewById(R.id.button_voice) }

    private val configurableCommitBtn: TextKeyView by lazy { findViewById(R.id.button_configurable_commit) }

    @Keep
    private val onSidebarSymbolsKeyChanged = ManagedPreference.OnChangeListener<String> { _, v ->
        updateSideBarItems(generateItems(v))
    }

    @Keep
    private val onVoiceInputBtnReplacedChanged =
        ManagedPreference.OnChangeListener<String> { _, v ->
            updateVoiceBtnState(v)
        }


    init {
        sidebarSymbolsKey.registerOnChangeListener(onSidebarSymbolsKeyChanged)
        voiceInputBtnReplaced.registerOnChangeListener(onVoiceInputBtnReplacedChanged)

        updateSideBarItems(generateItems(sidebarSymbolsKey.getValue()))
        updateVoiceBtnState(voiceInputBtnReplaced.getValue())
    }

    companion object {
        const val Name = "T9Text"
        fun generateItems(chars: String): List<KeyDef> {
            val items = ArrayList<KeyDef>()
            val chars = chars.trim().split(" ")
            chars.forEach { char ->
                items.add(CommitKey(char.trim(), textSize = 18f))
            }
            return items
        }

        val SideLayout: KeyDef = ColumnKey(
            percentWidth = 0.15f,
            variant = Variant.Alternative,
        )

        val Layout: List<List<KeyDef>> = listOf(
            listOf(
                PlaceHolderKey(),
                MixAlphabetKey("分词", "1", percentWidth = 0f),
                MixAlphabetKey("ABC", "2", percentWidth = 0f),
                MixAlphabetKey("DEF", "3", percentWidth = 0f),
                BackspaceKey(percentWidth = 0.15f),
            ), listOf(
                PlaceHolderKey(),
                MixAlphabetKey("GHI", "4", percentWidth = 0f),
                MixAlphabetKey("JKL", "5", percentWidth = 0f),
                MixAlphabetKey("MNO", "6", percentWidth = 0f),
                ClearKey(displayText = "清空", percentWidth = 0.15f),
            ), listOf(
                PlaceHolderKey(),
                MixAlphabetKey("PQRS", "7", percentWidth = 0f),
                MixAlphabetKey("TUV", "8", percentWidth = 0f),
                MixAlphabetKey("WXYZ", "9", percentWidth = 0f),
                VoiceKey(),
                ConfigurableCommitKey("@", percentWidth = 0.15f, variant = Variant.Alternative)
            ),             listOf(
                SymbolPickerKey(percentWidth = 0.15f, variant = Variant.Alternative),
                LayoutSwitchKey(
                    "?123", MixNumberKeyboard.Name, percentWidth = 0.15f, textSize = 15f
                ),
                SpaceKey(),
                LanguageKey(percentWidth = 0.15f, variant = Variant.Alternative),
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
            action is KeyAction.SymAction -> {
                if (action.sym == KeySym(FcitxKeyMapping.FcitxKey_period)) {
                    super.onAction(KeyAction.CommitAction(""), source)
                }
                action
            }
            action is KeyAction.CommitSelfAction -> {
                KeyAction.CommitAction(configurableCommitBtn.mainText.text.toString())
            }
            action is KeyAction.LayoutSwitchAction -> {
                if (action.act == MixNumberKeyboard.Name || action.act == NumberKeyboard.Name) {
                    var act = MixNumberKeyboard.Name
                    if (!AppPrefs.getInstance().keyboard.enableMixedNumberKeyboard.getValue()) {
                        act = NumberKeyboard.Name
                    }
                    KeyAction.LayoutSwitchAction(act)
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

    fun updateVoiceBtnState(v: String) {
        if (v.trim().isNotBlank()) {
            voiceInputBtn.visibility = GONE
            configurableCommitBtn.visibility = VISIBLE
            configurableCommitBtn.mainText.text = v
            return
        }
        voiceInputBtn.visibility = VISIBLE
        configurableCommitBtn.visibility = GONE
    }

    override fun shouldUpdateSidePanelWhenFcitxEvent(): Boolean{
        return true
    }
}