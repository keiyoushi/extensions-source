package eu.kanade.tachiyomi.extension.ar.mangalek

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

private const val BASE_URL_PREF_KEY = "customBaseUrl"
private const val DEFAULT_BASE_URL_PREF = "defaultBaseUrl"
private const val BASE_URL_PREF_TITLE = "رابط مخصص لمانجا ليك"
private const val SUMMARY_MIRROR = "يُستخدم عنوان الوصول حاليًا. أدخل رابطًا مخصصًا لتجاوزه"
private const val SUMMARY_CUSTOM = "الرابط المخصص نشط. قم بإفراغ هذا الحقل للعودة لاستخدام عنوان الوصول"

private const val MIRROR_PREF_KEY = "MIRROR"
private const val MIRROR_PREF_TITLE = "عنوان الوصول لمانجا ليك"
internal val MIRROR_PREF_ENTRY_VALUES = arrayOf(
    "https://lekmanga.net",
    "https://lekmanga.online",
    "https://like-manga.net",
    "https://lekmanga.site",
    "https://manga-leko.site",
)
private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]
private const val RESTART_TACHIYOMI = ".لتطبيق الإعدادات الجديدة Tachiyomi أعد تشغيل"

class Mangalek :
    Madara(
        "مانجا ليك",
        MIRROR_PREF_DEFAULT_VALUE,
        "ar",
        SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    ),
    ConfigurableSource {

    override val fetchGenres = false
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val chapterUrlSuffix = ""

    override val baseUrl by lazy {
        when {
            System.getenv("CI") == "true" -> MIRROR_PREF_ENTRY_VALUES.joinToString("#, ")
            !preferences.getString(BASE_URL_PREF_KEY, "").isNullOrBlank() -> preferences.getString(BASE_URL_PREF_KEY, "")!!
            else -> preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)!!
        }
    }

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val mirrorPref = ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = MIRROR_PREF_TITLE
            entries = MIRROR_PREF_ENTRY_VALUES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                true
            }
        }

        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_KEY
            title = BASE_URL_PREF_TITLE
            dialogTitle = BASE_URL_PREF_TITLE
            setDefaultValue("")
            summary = when (preferences.getString(BASE_URL_PREF_KEY, "").isNullOrBlank()) {
                true -> SUMMARY_MIRROR
                false -> SUMMARY_CUSTOM
            }

            setOnPreferenceChangeListener { pref, value ->
                pref.summary = when (value.toString().isNotBlank()) {
                    true -> SUMMARY_CUSTOM
                    false -> SUMMARY_MIRROR
                }

                Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                true
            }
        }

        screen.addPreference(mirrorPref)
        screen.addPreference(baseUrlPref)
    }

    init {
        preferences.getString(DEFAULT_BASE_URL_PREF, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != MIRROR_PREF_DEFAULT_VALUE) {
                preferences.edit()
                    // clear custom base URL
                    .putString(BASE_URL_PREF_KEY, "")
                    // reset mirror to first entry
                    .putString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)
                    // store new default mirror
                    .putString(DEFAULT_BASE_URL_PREF, MIRROR_PREF_DEFAULT_VALUE)
                    .apply()
            }
        }
    }

    private val formatOne = SimpleDateFormat("MMMM dd, yyyy", Locale("ar"))
    private val formatTwo = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun parseChapterDate(date: String?): Long {
        date ?: return 0L

        return try {
            formatOne.parse(date)!!.time
        } catch (_: ParseException) {
            try {
                formatTwo.parse(date)!!.time
            } catch (_: ParseException) {
                0L
            }
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = POST(
        "$baseUrl/wp-admin/admin-ajax.php",
        headers,
        FormBody.Builder()
            .add("action", "wp-manga-search-manga")
            .add("title", query)
            .build(),
    )

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString(body.string())

    @Serializable
    data class SearchResponseDto(
        val data: List<SearchEntryDto>,
        val success: Boolean,
    )

    @Serializable
    data class SearchEntryDto(
        val url: String = "",
        val title: String = "",
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()

        if (!dto.success) {
            return MangasPage(emptyList(), false)
        }

        val manga = dto.data.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.url)
                title = it.title
            }
        }

        return MangasPage(manga, false)
    }
}
