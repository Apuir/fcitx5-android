/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.picker

import android.annotation.SuppressLint
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.*
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Border
import org.fcitx.fcitx5.android.input.keyboard.KeyDef.Appearance.Variant
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.above
import splitties.views.dsl.constraintlayout.below
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.centerHorizontally
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.matchConstraints
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.core.add
import splitties.views.dsl.core.view
import splitties.views.imageResource
import timber.log.Timber
import kotlin.text.ifEmpty

@SuppressLint("ViewConstructor")
class PickerLayout(context: Context, theme: Theme, switchKey: KeyDef) :
    ConstraintLayout(context) {

    class Keyboard(context: Context, theme: Theme, switchKey: KeyDef) : BaseKeyboard(
        context, theme, listOf(
            listOf(
                LayoutSwitchKey("Abc", "", percentWidth = 0.15f, textSize = 15f),
                LanguageKey(percentWidth = 0.15f, variant = Variant.Alternative),
                SpaceKey(),
                SimpleCommaKey(".",0.15f, Variant.Alternative),
                ReturnKey(percentWidth = 0.15f)
            )
        )
    ) {
        val `return`: ImageKeyView by lazy { findViewById(R.id.button_return) }

        val space: TextKeyView by lazy { findViewById(R.id.button_space) }

        override fun onReturnDrawableUpdate(returnDrawable: Int) {
            `return`.img.imageResource = returnDrawable
        }

        override fun onInputMethodUpdate(ime: InputMethodEntry) {
            space.mainText.text = buildString {
                append(ime.displayName)
                ime.subMode.run { label.ifEmpty { name.ifEmpty { null } } }?.let { append(" [$it] ") }
            }
        }
    }

    val embeddedKeyboard = Keyboard(context, theme, switchKey)

    val pager = view(::ViewPager2) { }

    val tabsUi = PickerTabsUi(context, theme)

    val paginationUi = PickerPaginationUi(context, theme)

    init {
        add(pager, lParams {
            topOfParent()
            centerHorizontally()
            above(embeddedKeyboard)
        })
        add(embeddedKeyboard, lParams {
            below(pager)
            centerHorizontally()
            bottomOfParent()
            matchConstraintPercentHeight = 0.25f
        })
        add(paginationUi.root, lParams(matchConstraints, dp(2)) {
            centerHorizontally()
            below(pager, dp(-1))
        })
    }
}