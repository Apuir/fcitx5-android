/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.graphics.Typeface
import android.view.View
import androidx.annotation.DrawableRes
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeyState
import org.fcitx.fcitx5.android.core.KeyStates
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import org.fcitx.fcitx5.android.input.picker.PickerWindow

val NumLockState = KeyStates(KeyState.NumLock, KeyState.Virtual)

class SymbolPickerKey(
    percentWidth: Float = 0.15f,
    variant: Variant = Variant.Alternative,
) : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_at_24,
        percentWidth = percentWidth,
        variant = variant,
    ), setOf(
        Behavior.Press(KeyAction.PickerSwitchAction(PickerWindow.Key.Symbol))
    )
)
class SimplePunctuationKey(
    displayText: String,
    percentWidth: Float,
    variant: Variant,
    keySym: Int = FcitxKeyMapping.FcitxKey_period,
    useCommit: Boolean = false,
) : KeyDef(
    Appearance.Text(
        displayText = displayText,
        textSize = 23f,
        percentWidth = percentWidth,
        variant = variant,
    ), setOf(
        if (useCommit) {
            Behavior.Press(KeyAction.CommitAction(displayText))
        } else {
            Behavior.Press(
                KeyAction.SymAction(
                    KeySym(keySym),
                    KeyStates(KeyState.Virtual, KeyState.CapsLock)
                )
            )
        }
    )
)
class SymbolKey(
    val symbol: String,
    percentWidth: Float = 0.1f,
    variant: Variant = Variant.Normal,
    popup: Array<Popup>? = null
) : KeyDef(
    Appearance.Text(
        displayText = symbol, textSize = 23f, percentWidth = percentWidth, variant = variant
    ), setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(symbol))
    ), popup ?: arrayOf(
        Popup.Preview(symbol), Popup.Keyboard(symbol)
    )
)

class AlphabetKey(
    val character: String,
    val punctuation: String,
    variant: Variant = Variant.Normal,
    popup: Array<Popup>? = null
) : KeyDef(
    Appearance.AltText(
        displayText = character, altText = punctuation, textSize = 23f, variant = variant
    ), setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(character)),
        Behavior.Swipe(KeyAction.FcitxKeyAction(punctuation))
    ), popup ?: arrayOf(
        Popup.Keyboard(character)
    )
)


class PlaceHolderKey(percentWidth: Float = 0.15f) : MixAlphabetKey(
    character = "PlaceHolder",
    punctuation = "#",
    visibility = View.INVISIBLE,
    percentWidth = percentWidth,
    variant = Variant.Normal
)

class PinYinCandidateKey(
    pos: Int,
    raw: String,
    pinYin: String,
) : KeyDef(
    Appearance.Text(
        pinYin,
        textSize = 15f,
        percentWidth = 0.15f,
        variant = Variant.Normal,
        border = Border.Off,
        visibility = View.VISIBLE,
    ),
    setOf(
        Behavior.Press(KeyAction.SelectPinYinAction(pos, raw, pinYin))
    ),
    arrayOf(),
)

class CommitKey(
    content: String,
    textSize: Float = 15f,
    border: Border = Border.Default,
    percentWidth: Float = 0.15f,
    variant: Variant = Variant.Alternative,
) : KeyDef(
    Appearance.Text(
        content,
        textSize = textSize,
        percentWidth = percentWidth,
        variant = variant,
        border = border,
        visibility = View.VISIBLE,
    ),
    setOf(
        Behavior.Press(KeyAction.CommitAction(content))
    ),
    arrayOf(),
)

class ConfigurableCommitKey(
    content: String,
    textSize: Float = 15f,
    border: Border = Border.On,
    percentWidth: Float = 0.15f,
    variant: Variant = Variant.Alternative,
) : KeyDef(
    Appearance.Text(
        content,
        textSize = textSize,
        percentWidth = percentWidth,
        variant = variant,
        border = border,
        visibility = View.VISIBLE,
        viewId = R.id.button_configurable_commit
    ),
    setOf(
        Behavior.Press(KeyAction.CommitSelfAction(""))
    ),
    arrayOf(),
)

open class MixAlphabetKey(
    val character: String,
    val punctuation: String,

    variant: Variant = Variant.Normal,
    popup: Array<Popup>? = null,
    percentWidth: Float = 0.232f,
    textSize: Float = 16f,
    visibility: Int = View.VISIBLE,
) : KeyDef(
    Appearance.AltText(
        displayText = character,
        altText = punctuation,
        textSize = textSize,
        variant = variant,
        percentWidth = percentWidth,
        visibility = visibility,
    ), setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(punctuation)),
        Behavior.Swipe(KeyAction.CommitAction(punctuation))
    ), popup ?: arrayOf(
        Popup.Keyboard(punctuation)
    )
)

class ColumnKey(
    variant: Variant = Variant.Normal,
    popup: Array<Popup>? = null,
    percentWidth: Float = 0.212f,
) : KeyDef(
    Appearance.Column(
        variant = variant,
        percentWidth = percentWidth,
    ), setOf(
    ), popup ?: arrayOf(
    )
)

class AlphabetDigitKey(
    val character: String, altText: String, val sym: Int, popup: Array<Popup>? = null
) : KeyDef(
    Appearance.AltText(
        displayText = character, altText = altText, textSize = 23f
    ), setOf(
        Behavior.Press(KeyAction.FcitxKeyAction(character)),
        Behavior.Swipe(KeyAction.SymAction(KeySym(sym), NumLockState))
    ), popup ?: arrayOf(
        Popup.AltPreview(character, altText), Popup.Keyboard(character)
    )
) {
    constructor(
        char: String, digit: Int, popup: Array<Popup>? = null
    ) : this(
        char, digit.toString(), FcitxKeyMapping.FcitxKey_KP_0 + digit, popup
    )
}

class CapsKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_capslock_none,
        viewId = R.id.button_caps,
        percentWidth = 0.15f,
        variant = Variant.Alternative
    ), setOf(
        Behavior.Press(KeyAction.CapsAction(false)),
        Behavior.LongPress(KeyAction.CapsAction(true)),
        Behavior.DoubleTap(KeyAction.CapsAction(true))
    )
)

class LayoutSwitchKey(
    displayText: String,
    val to: String = "",
    percentWidth: Float = 0.15f,
    variant: Variant = Variant.Alternative,
    textSize: Float = 16f
) : KeyDef(
    Appearance.Text(
        displayText,
        textSize = textSize,
        textStyle = Typeface.BOLD,
        percentWidth = percentWidth,
        variant = variant
    ), setOf(
        Behavior.Press(KeyAction.LayoutSwitchAction(to))
    )
)

class BackspaceKey(
    percentWidth: Float = 0.15f, variant: Variant = Variant.Alternative
) : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_backspace_24,
        percentWidth = percentWidth,
        variant = variant,
        viewId = R.id.button_backspace,
        soundEffect = InputFeedbacks.SoundEffect.Delete
    ), setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_BackSpace))),
        Behavior.Repeat(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_BackSpace)))
    )
)


class ClearKey(
    displayText: String, percentWidth: Float = 0.18f, variant: Variant = Variant.Alternative
) : KeyDef(
    Appearance.Text(
        displayText, textSize = 16f, percentWidth = percentWidth, variant = variant
    ), setOf(
        Behavior.Press(KeyAction.ClearAction)
    )
)

class VoiceKey(
    percentWidth: Float = 0.15f, variant: Variant = Variant.Alternative
) : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_keyboard_voice_24,
        percentWidth = percentWidth,
        variant = variant,
        viewId = R.id.button_voice,
        soundEffect = InputFeedbacks.SoundEffect.Standard
    ), setOf(
        Behavior.Press(KeyAction.VoiceAction),
    )
)


class QuickPhraseKey : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_format_quote_24,
        variant = Variant.Alternative,
        viewId = R.id.button_quickphrase
    ), setOf(
        Behavior.Press(KeyAction.QuickPhraseAction), Behavior.LongPress(KeyAction.UnicodeAction)
    )
)

class CommaKey(
    displayText: String = ".",
    keySym: Int = FcitxKeyMapping.FcitxKey_period,
    percentWidth: Float,
    variant: Variant,
) : KeyDef(
    Appearance.ImageText(
        displayText = displayText,
        textSize = 23f,
        percentWidth = percentWidth,
        variant = variant,
        src = R.drawable.ic_baseline_tag_faces_24
    ), setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(keySym), KeyStates(KeyState.Virtual, KeyState.CapsLock)))
    ), arrayOf(
        Popup.Preview(""), Popup.Menu(
            arrayOf(
                Popup.Menu.Item(
                    "Symbol", R.drawable.ic_baseline_at_24, KeyAction.PickerSwitchAction(
                        PickerWindow.Key.Symbol
                    )
                ),
                Popup.Menu.Item(
                    "Emoji", R.drawable.ic_baseline_tag_faces_24, KeyAction.PickerSwitchAction(
                        PickerWindow.Key.Emoji,
                    )
                ), Popup.Menu.Item(
                    "QuickPhrase",
                    R.drawable.ic_baseline_format_quote_24,
                    KeyAction.QuickPhraseAction
                ), Popup.Menu.Item(
                    "Unicode", R.drawable.ic_logo_unicode, KeyAction.UnicodeAction
                )
            )
        )
    )
)


class SimpleCommaKey(
    displayText: String = ",",
    percentWidth: Float,
    variant: Variant,
) : KeyDef(
    Appearance.ImageText(
        displayText = displayText,
        textSize = 23f,
        percentWidth = percentWidth,
        variant = variant,
        src = R.drawable.ic_baseline_tag_faces_24
    ), setOf(
        Behavior.Press(KeyAction.CommitAction(displayText))
    ), arrayOf(
        Popup.Preview(""), Popup.Menu(
            arrayOf(
                Popup.Menu.Item(
                    "Symbol", R.drawable.ic_baseline_at_24, KeyAction.PickerSwitchAction(
                        PickerWindow.Key.Symbol
                    )
                ),
                Popup.Menu.Item(
                    "Number", R.drawable.ic_baseline_number123_24, KeyAction.LayoutSwitchAction(
                        NumberKeyboard.Name
                    )
                ),
                Popup.Menu.Item(
                    "Emoji", R.drawable.ic_baseline_tag_faces_24, KeyAction.PickerSwitchAction(
                        PickerWindow.Key.Emoji,
                    )
                ),
            )
        )
    )
)

class LanguageKey(
    percentWidth: Float = 0f, variant: Variant = Variant.Alternative
) : KeyDef(
    Appearance.Image(
        percentWidth = percentWidth,
        src = R.drawable.ic_baseline_language_24,
        variant = variant,
        viewId = R.id.button_lang,
    ), setOf(
        Behavior.Press(KeyAction.LangSwitchAction),
        Behavior.LongPress(KeyAction.ShowInputMethodPickerAction)
    )
)

class SpaceKey(
    percentWidth: Float = 0f,
    variant: Variant =  Variant.Alternative
) : KeyDef(
    Appearance.Text(
        displayText = " ",
        textSize = 13f,
        percentWidth = percentWidth,
        border = Border.Special,
        viewId = R.id.button_space,
        soundEffect = InputFeedbacks.SoundEffect.SpaceBar,
        variant = variant
    ), setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_space))),
        Behavior.LongPress(KeyAction.SpaceLongPressAction),
        Behavior.Swipe(KeyAction.CommitAction("0"))
    )
)

class ReturnKey(percentWidth: Float = 0.15f) : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_keyboard_return_24,
        percentWidth = percentWidth,
        variant = Variant.Accent,
        border = Border.Special,
        viewId = R.id.button_return,
        soundEffect = InputFeedbacks.SoundEffect.Return
    ),
    setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_Return)))
    ),
    arrayOf(
        Popup.Menu(
            arrayOf(
                Popup.Menu.Item(
                    "Emoji", R.drawable.ic_baseline_tag_faces_24, KeyAction.PickerSwitchAction()
                )
            )
        )
    ),
)

class ImageLayoutSwitchKey(
    @DrawableRes icon: Int,
    to: String,
    percentWidth: Float = 0.1f,
    variant: Variant = Variant.AltForeground,
    viewId: Int = -1
) : KeyDef(
    Appearance.Image(
        src = icon, percentWidth = percentWidth, variant = variant, viewId = viewId
    ), setOf(
        Behavior.Press(KeyAction.LayoutSwitchAction(to))
    )
)

class ImagePickerSwitchKey(
    @DrawableRes icon: Int,
    to: PickerWindow.Key,
    percentWidth: Float = 0.1f,
    variant: Variant = Variant.AltForeground,
    viewId: Int = -1
) : KeyDef(
    Appearance.Image(
        src = icon, percentWidth = percentWidth, variant = variant, viewId = viewId
    ), setOf(
        Behavior.Press(KeyAction.PickerSwitchAction(to))
    )
)

class TextPickerSwitchKey(
    text: String,
    to: PickerWindow.Key,
    percentWidth: Float = 0.1f,
    variant: Variant = Variant.AltForeground,
    viewId: Int = -1
) : KeyDef(
    Appearance.Text(
        displayText = text,
        textSize = 16f,
        percentWidth = percentWidth,
        variant = variant,
        viewId = viewId,
        textStyle = Typeface.BOLD
    ), setOf(
        Behavior.Press(KeyAction.PickerSwitchAction(to))
    )
)

class MiniSpaceKey(percentWidth: Float = 0.15f) : KeyDef(
    Appearance.Image(
        src = R.drawable.ic_baseline_space_bar_24,
        percentWidth = percentWidth,
        variant = Variant.Alternative,
        viewId = R.id.button_mini_space
    ), setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(FcitxKeyMapping.FcitxKey_space)))
    )
)

class NumPadKey(
    displayText: String,
    val sym: Int,
    textSize: Float = 16f,
    percentWidth: Float = 0.1f,
    variant: Variant = Variant.Normal
) : KeyDef(
    Appearance.Text(
        displayText, textSize = textSize, percentWidth = percentWidth, variant = variant
    ), setOf(
        Behavior.Press(KeyAction.SymAction(KeySym(sym), NumLockState))
    )
)
