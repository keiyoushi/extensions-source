package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import android.content.SharedPreferences

internal const val BLOCK_PREF = "BLOCK_GENRES_LIST"
internal const val USERNAME_PREF = "username"
internal const val PASSWORD_PREF = "password"
internal const val CHECKIN_PREF = "auto_checkin"
internal const val CHECKIN_DATE_PREF = "last_checkin_date"

internal val SharedPreferences.blockList: List<String>
    get() = getString(BLOCK_PREF, "")!!.substringBefore("//").trim().lowercase().split(' ')

internal val SharedPreferences.credentials: Pair<String, String>?
    get() {
        val username = getString(USERNAME_PREF, null)?.trim()
        val password = getString(PASSWORD_PREF, null)
        if (username.isNullOrEmpty() || password.isNullOrEmpty()) return null
        return username to password
    }

private const val OLD_MIRROR_PREF = "useMirrorWebsitePreference"
private const val MIRROR_PREF = "preferred_mirror"
private const val MIRROR_COUNT = 4

// The hand-rolled mirror list became the DSL mirrors; its first entries are the same hosts
internal fun SharedPreferences.preferenceMigration() {
    val oldMirror = getString(OLD_MIRROR_PREF, null) ?: return
    edit().apply {
        oldMirror.toIntOrNull()?.takeIf { it in 0 until MIRROR_COUNT }?.let { putString(MIRROR_PREF, it.toString()) }
        remove(OLD_MIRROR_PREF)
        remove("baseUrlList")
        remove("defaultBaseUrlList")
    }.apply()
}
