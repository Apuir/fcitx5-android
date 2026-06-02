package org.fcitx.fcitx5.android.utils

import android.os.Build
import androidx.annotation.RequiresApi

class Strings {
    enum class WidthType { FULLWIDTH, HALFWIDTH, MIXED }

    companion object {

        fun classifyWidth(text: String): WidthType {
            var hasFull = false
            var hasHalf = false

            text.codePoints().forEach { cp ->
                when {
                    isFullwidthCodePoint(cp) -> hasFull = true
                    isHalfwidthCodePoint(cp) -> hasHalf = true
                    else -> {
                        hasFull = true; hasHalf = true
                    } // unknown, treat as both to be safe? 或者不处理
                }
            }
            return when {
                hasFull && !hasHalf -> WidthType.FULLWIDTH
                !hasFull && hasHalf -> WidthType.HALFWIDTH
                else -> WidthType.MIXED
            }
        }

        fun isFullwidthCodePoint(cp: Int): Boolean {
            return cp in 0xFF01..0xFF5E || cp in 0xFFE0..0xFFE6 || cp in 0x3000..0x303F || // CJK符号和标点
                    cp in 0x3040..0x309F || // 平假名
                    cp in 0x30A0..0x30FF || // 片假名
                    cp in 0x31F0..0x31FF || // 片假名扩展
                    cp in 0x3300..0x33FF || // CJK兼容
                    cp in 0x4E00..0x9FFF || // CJK统一汉字
                    cp in 0xF900..0xFAFF || // CJK兼容汉字
                    cp in 0xFE30..0xFE4F || // CJK兼容形式
                    cp in 0x2E80..0x2EFF || // CJK部首补充
                    cp in 0x2F00..0x2FDF || // 康熙部首
                    cp in 0x2FF0..0x2FFF || // 表意文字描述字符
                    cp in 0x3400..0x4DBF || // CJK扩展A
                    cp in 0x20000..0x2A6DF || // CJK扩展B
                    cp in 0x2A700..0x2B73F || // CJK扩展C
                    cp in 0x2B740..0x2B81F || // CJK扩展D
                    cp in 0x2B820..0x2CEAF || // CJK扩展E
                    cp in 0x2CEB0..0x2EBEF || // CJK扩展F
                    cp in 0x30000..0x3134F || // CJK扩展G
                    cp in 0x31350..0x323AF || // CJK扩展H
                    cp in 0xAC00..0xD7AF || // 韩文音节
                    cp in 0x1100..0x11FF || // 韩文辅音/元音
                    cp in 0x3130..0x318F || // 兼容韩文
                    cp in 0xA960..0xA97C || // 韩文扩展A
                    cp in 0xD7B0..0xD7FB    // 韩文扩展B
        }

        fun isHalfwidthCodePoint(cp: Int): Boolean {
            return cp in 0x0020..0x007E || // ASCII可打印
                    cp in 0xFF65..0xFF9F    // 半角片假名
        }
    }
}