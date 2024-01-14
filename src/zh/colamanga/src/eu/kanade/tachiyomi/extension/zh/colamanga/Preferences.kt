package eu.kanade.tachiyomi.extension.zh.colamanga

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.ListPreference

fun getPreferencesInternal(context: Context) =
    arrayOf(
        ListPreference(context).apply {
            key = KEYS_URL_PREF
            title = "密钥网址"
            summary = "%s\n修改后，需要重启应用才能生效"
            entries = arrayOf("GitHub", "jsDelivr")
            entryValues = arrayOf(GITHUB, JSDELIVR)
            setDefaultValue(GITHUB)
        },
    )

val SharedPreferences.keysUrl
    get() = getString(KEYS_URL_PREF, GITHUB)!!

private const val KEYS_URL_PREF = "keysUrlPreference"
const val GITHUB = "https://raw.githubusercontent.com/he0119/autoCI-cocomanga/main/coco_keys.json"
const val JSDELIVR = "https://cdn.jsdelivr.net/gh/he0119/autoCI-cocomanga@main/coco_keys.json"
