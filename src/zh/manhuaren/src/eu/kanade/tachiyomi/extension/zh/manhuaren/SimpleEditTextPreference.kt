package eu.kanade.tachiyomi.extension.zh.manhuaren
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import android.content.Context
import androidx.preference.EditTextPreference

class SimpleEditTextPreference(context: Context?) : EditTextPreference(context) {
    override fun getSummary(): CharSequence {
        return text ?: ""
    }
}
