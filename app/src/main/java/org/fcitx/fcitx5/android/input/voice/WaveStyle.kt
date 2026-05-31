package org.fcitx.fcitx5.android.input.voice

enum class WaveStyle(val value: Int) {
    /** 克制球形中心发散声波 */
    SPHERE_RIPPLE(0),

    PARTICLE_WAVE(1);
//    /** 备用传统横向线性条状波纹（留作未来扩展） */
//    SOLAR_SYSTEM(1);

    companion object {
        fun fromInt(value: Int): WaveStyle =
            entries.firstOrNull { it.value == value } ?: SPHERE_RIPPLE
    }
}