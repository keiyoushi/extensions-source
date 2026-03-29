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

        try {
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
        } catch (_: Exception) {
            return i18nCache[lang] ?: getDefaultI18n()
        }
    }

    companion object {
        fun getDefaultI18n(): I18nDictionary = I18nDictionary(
            home = I18nHomeDictionary(
                updates = I18nHomeUpdatesDictionary(
                    buttons = I18nHomeButtonsDictionary(
                        language = mapOf(
                            "all" to "All",
                            "spanish" to "Spanish",
                            "english" to "English",
                            "chinese" to "Chinese",
                            "raw" to "Raw",
                        ),
                        genres = mapOf(
                            "all" to "All",
                            "mature" to "Mature",
                            "normal" to "Normal",
                        ),
                    ),
                ),
                lastUpdatesNormal = "Recent Updates",
            ),
            library = I18nLibraryDictionary(
                title = "Library",
                search = "Search",
                sort = mapOf(
                    "title" to "Title",
                    "type" to "Type",
                    "rating" to "Rating",
                    "date" to "Date",
                ),
                filter = mapOf(
                    "title" to "Title",
                    "category" to "Category",
                    "language" to "Language",
                    "sortBy" to "Sort By",
                ),
            ),
        )
    }
}
