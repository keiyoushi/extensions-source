package eu.kanade.tachiyomi.extension.zh.manhuaren

import android.content.Context
import androidx.preference.EditTextPreference

class SimpleEditTextPreference(context: Context?) : EditTextPreference(context) {
    override fun getSummary(): CharSequence {
        return text ?: ""
    }
}
