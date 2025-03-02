package eu.kanade.tachiyomi.extension.ko.newtoki

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import keiyoushi.utils.getPreferences

const val MANATOKI_ID = 2526381983439079467L // "NewToki/ko/1"
const val NEWTOKI_ID = 1977818283770282459L // "NewToki (Webtoon)/ko/1"

const val MANATOKI_PREFIX = "manatoki"
const val NEWTOKI_PREFIX = "newtoki"

val manaTokiPreferences = getPreferences(MANATOKI_ID).migrate()
val newTokiPreferences = getPreferences(NEWTOKI_ID).migrate()

fun getPreferencesInternal(context: Context) = arrayOf(

    EditTextPreference(context).apply {
        key = DOMAIN_NUMBER_PREF
        title = domainNumberTitle()
        summary = domainNumberSummary()
        setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as String
            if (value.isEmpty() || value != value.trim()) {
                false
            } else {
                domainNumber = value
                true
            }
        }
    },

    ListPreference(context).apply {
        key = RATE_LIMIT_PERIOD_PREF
        title = rateLimitTitle()
        summary = "%s\n" + requiresAppRestart()

        val values = Array(RATE_LIMIT_PERIOD_MAX) { (it + 1).toString() }
        entries = Array(RATE_LIMIT_PERIOD_MAX) { rateLimitEntry(values[it]) }
        entryValues = values

        setDefaultValue(RATE_LIMIT_PERIOD_DEFAULT)
    },
)

var SharedPreferences.domainNumber: String
    get() = getString(DOMAIN_NUMBER_PREF, "")!!
    set(value) = edit().putString(DOMAIN_NUMBER_PREF, value).apply()

val SharedPreferences.rateLimitPeriod: Int
    get() = getString(RATE_LIMIT_PERIOD_PREF, RATE_LIMIT_PERIOD_DEFAULT)!!.toInt().coerceIn(1, RATE_LIMIT_PERIOD_MAX)

private fun SharedPreferences.migrate(): SharedPreferences {
    if ("Override BaseUrl" !in this) return this // already migrated
    val editor = edit().clear() // clear all legacy preferences listed below
    val oldValue = try { // this was a long
        getLong(RATE_LIMIT_PERIOD_PREF, -1).toInt()
    } catch (_: ClassCastException) {
        -1
    }
    if (oldValue != -1) { // convert to string
        val newValue = oldValue.coerceIn(1, RATE_LIMIT_PERIOD_MAX)
        editor.putString(RATE_LIMIT_PERIOD_PREF, newValue.toString())
    }
    editor.apply()
    return this
}

/**
 * Don't use the following legacy keys:
 * - "Override BaseUrl"
 * - "overrideBaseUrl_v${AppInfo.getVersionName()}"
 * - "Enable Latest (Experimental)"
 * - "fetchLatestExperiment"
 * - "Fetch Latest with detail (Optional)"
 * - "fetchLatestWithDetail"
 * - "Rate Limit Request Period Seconds"
 */

private const val DOMAIN_NUMBER_PREF = "domainNumber"
private const val RATE_LIMIT_PERIOD_PREF = "rateLimitPeriod"
private const val RATE_LIMIT_PERIOD_DEFAULT = 2.toString()
private const val RATE_LIMIT_PERIOD_MAX = 9
