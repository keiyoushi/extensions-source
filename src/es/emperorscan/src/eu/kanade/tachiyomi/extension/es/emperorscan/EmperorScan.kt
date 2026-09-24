package eu.kanade.tachiyomi.extension.es.emperorscan

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class EmperorScan :
    MadaraNoAjax(),
    ConfigurableSource {
    override val supportsPostId = false

    override val chapterDateFormat = DateTimeFormatter.ofPattern("MMMM dd, yyyy", Locale.forLanguageTag("es"))

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override fun OkHttpClient.Builder.configureClient() = rateLimit(2) { it.host == baseUrlHost }

    override val filterGenresSelector = ".filters"
    override fun archiveSelector() = "a.acard"
    override val archiveUrlSelector = "*"
    override val archiveTitleSelector = ".ac-t"

    override val mangaDetailsSelectorTitle = "div.hcol > .htitle"
    override val mangaDetailsSelectorStatus = "div.hcol > .htags > .htag--status"
    override val mangaDetailsSelectorDescription = "div#syn > p"
    override val mangaDetailsSelectorThumbnail = "div.hposter__card > img"
    override val mangaDetailsSelectorGenre = "div.hcol > .hchips--genres > a.chip"
    override val mangaDetailsSelectorTag = "div.hcol > .hchips--tags > a.chip"

    override fun parseDetails(document: Document, id: String, preserveUrl: String?) = super.parseDetails(document, id, preserveUrl).apply {
        description = description?.replace("HAZ CLICK AQUÍ PARA UNIRTE A NUESTRO DISCORD", "", ignoreCase = false)?.trim()

        if (removePremium && !genre.isNullOrEmpty()) {
            val allCategories = genre!!.split(",").map { it.trim() }

            val filteredCategories = allCategories.filterNot { item ->
                item.contains("Vip", ignoreCase = true) ||
                    item.contains("Premium", ignoreCase = true) ||
                    item.contains("Emperor scan", ignoreCase = true)
            }

            genre = filteredCategories.joinToString(", ")
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override suspend fun fetchChapters(
        mangaPath: String,
        id: String,
        mangaPage: Document?,
    ) = mangaPage!!.selectFirst("script#mk-chapters-data")!!.data()
        .parseAs<ChapterListDto>().items.filterNot { chapter ->
            removePremium && (
                chapter.name.contains("Vip", ignoreCase = true) ||
                    chapter.name.contains("Soberano", ignoreCase = true) ||
                    chapter.name.contains("Premium", ignoreCase = true) ||
                    chapter.url.contains("/membership-levels/", ignoreCase = true) ||
                    chapter.st.contains("locked", ignoreCase = true)
                )
        }.map { chapterDto ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterDto.url)
                name = chapterDto.name
                date_upload = parseChapterDate(chapterDto.ago)
            }
        }

    private val preferences: SharedPreferences = getPreferences()

    private val removePremium get() =
        preferences.getBoolean(REMOVE_PREMIUM_CHAPTERS, REMOVE_PREMIUM_CHAPTERS_DEFAULT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = REMOVE_PREMIUM_CHAPTERS
            title = "Filtrar capítulos VIP"
            summary = "Oculta automáticamente los capítulos VIP"
            setDefaultValue(REMOVE_PREMIUM_CHAPTERS_DEFAULT)
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, "Para aplicar los cambios, actualiza la lista de capítulos", Toast.LENGTH_LONG).show()
                true
            }
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val REMOVE_PREMIUM_CHAPTERS = "removePremiumChapters"
        private const val REMOVE_PREMIUM_CHAPTERS_DEFAULT = true
    }
}
