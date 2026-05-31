package org.fcitx.fcitx5.android.data

import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.prefs.ManagedPreferenceEnum

class LayoutData {
    enum class VoiceKeyboardStyle(override val stringRes: Int) : ManagedPreferenceEnum {
        Default(R.string.default_),
        ParticleRing(R.string.particle_ring);
    }
}