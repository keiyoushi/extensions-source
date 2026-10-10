package eu.kanade.tachiyomi.extension.en.sirenscans

import eu.kanade.tachiyomi.multisrc.keyoapp.KeyoappV2
import keiyoushi.annotation.Source

@Source
abstract class SirenScans : KeyoappV2() {
    init {
        val oldKey = "pref_show_locked_chap"
        if (preferences.contains(oldKey)) {
            preferences.edit()
                .putBoolean("pref_show_paid_chap", preferences.getBoolean(oldKey, false))
                .remove(oldKey)
                .apply()
        }
    }
}
