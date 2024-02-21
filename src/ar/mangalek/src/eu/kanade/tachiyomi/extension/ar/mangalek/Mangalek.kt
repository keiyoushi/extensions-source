package eu.kanade.tachiyomi.extension.ar.mangalek

import android.app.Application
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.configurablebaseurl.ConfigurableBaseUrl
import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

private const val defaultBaseUrl = "https://manga-lek.net"

class Mangalek :
    Madara(
        "مانجا ليك",
        defaultBaseUrl,
        "ar",
        SimpleDateFormat("MMMM dd, yyyy", Locale("ar")),
    ),
    ConfigurableSource {

    private val preferences = Injekt.get<Application>()
        .getSharedPreferences("source_$id", 0x0000)

    private val configurableBaseUrl = ConfigurableBaseUrl(defaultBaseUrl, preferences, lang)

    override val baseUrl get() = configurableBaseUrl.getBaseUrl()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        configurableBaseUrl.addBaseUrlPreference(screen)
    }

    override val fetchGenres = false
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val chapterUrlSuffix = ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        POST(
            "$baseUrl/wp-admin/admin-ajax.php",
            headers,
            FormBody.Builder()
                .add("action", "wp-manga-search-manga")
                .add("title", query)
                .build(),
        )

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

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
