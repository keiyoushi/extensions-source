package eu.kanade.tachiyomi.extension.all.sakuramanhwa

import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.OkHttpClient
import okio.IOException

internal class I18nHelper(
    val baseUrl: String,
    val client: OkHttpClient,
    val preference: SharedPreferences,
) {
    private val i18nCache: HashMap<String, I18nDictionary> = run {
        val i18nJson = preference.getString(APP_I18N_KEY, null) ?: return@run hashMapOf()
        try {
            i18nJson.parseAs<HashMap<String, I18nDictionary>>()
        } catch (_: Exception) {
            hashMapOf()
        }
    }

    @Synchronized
    fun getI18nByLanguage(lang: String): I18nDictionary {
        var i18nDictionary = i18nCache[lang]
        if (i18nDictionary != null) {
            return i18nDictionary
        }

        val request = GET("$baseUrl/assets/i18n/$lang.json?v=2")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Unexpected get i18n(${request.url}) error")
        }
        i18nDictionary = response.parseAs<I18nDictionary>()
        i18nCache[lang] = i18nDictionary

        preference.edit().putString(APP_I18N_KEY, i18nCache.toJsonString()).apply()

        return i18nDictionary
    }
}
