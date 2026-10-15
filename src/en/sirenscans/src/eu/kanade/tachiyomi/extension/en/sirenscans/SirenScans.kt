package eu.kanade.tachiyomi.extension.en.sirenscans

import eu.kanade.tachiyomi.multisrc.keyoapp.KeyoappV2
import keiyoushi.annotation.Source

@Source
abstract class SirenScans : KeyoappV2() {
    init {
        val oldKey = "pref_show_locked_chap"
        if (preferences.contains(oldKey)) {
            preferences.edit()
                .putBoolean(SHOW_PAID_CHAPTERS_PREF, preferences.getBoolean(oldKey, false))
                .remove(oldKey)
                .apply()
        }
    }
}
