package eu.kanade.tachiyomi.extension.pt.hentaitecanet

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.multisrc.madara.GenreRoute
import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.textOrNull
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Source
abstract class HentaiTecaNet :
    MadaraNoAjax(),
    ConfigurableSource {
    override val supportsPostId = false
    override val mangaSubString = "obra"
    override val chapterDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

    override val mangaDetailsSelectorStatus = "div.post-status .summary-heading:contains(Status) + .summary-content"
    override val seriesTypeSelector = ".summary-heading:contains(Tipo) + .summary-content"
    override val completedStatus = arrayOf("completa")
    override val ongoingStatus = arrayOf("em lançamento")
    override val hiatusStatus = arrayOf("hiatus")
    override val cancelledStatus = arrayOf("cancelada")
    override val statusFilterOptions = listOf(
        "Em Lançamento" to "on-going",
        "Completa" to "end",
        "Cancelada" to "canceled",
        "Hiatus" to "on-hold",
        "A Caminho" to "upcoming",
    )

    private val preferences by getPreferencesLazy()

    override fun getHomeUrl() = "$baseUrl/$mangaSubString/?m_orderby=views"

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = searchUrlBuilder(page, query).apply {
            filters.filterIsInstance<UrlFilter>().forEach { it.addTo(this) }
        }.build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return MangasPage(parseArchive(document), document.selectFirst(nextPageSelector()) != null)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.takeIf { it.size > 1 && it[0] == mangaSubString && it[1].isNotEmpty() && it[1] != "page" }?.get(1) ?: return null
        return super.getMangaByUrl(url.newBuilder().encodedPath("/$mangaSubString/$slug/").query(null).build())
    }

    override suspend fun fetchRelatedMangaList(manga: SManga) = fetchRelatedMangaList(manga.url, relatedGenres(manga))

    override fun parseDetails(document: Document, id: String, preserveUrl: String?): SManga = super.parseDetails(document, id, preserveUrl).apply {
        // The details image srcset stops at 386 px, og:image is the original upload
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("abs:content") ?: thumbnail_url
        // Type first, the theme appends it
        genre = (listOfNotNull(document.selectFirst(seriesTypeSelector)?.textOrNull()) + genre.orEmpty().split(", "))
            .filter(String::isNotEmpty).distinctBy(String::lowercase).joinToString().ifEmpty { null }

        val votes = document.selectFirst("#countrate")?.text()?.substringBefore(' ')?.toIntOrNull()?.takeIf { it > 0 }
        val rating = document.selectFirst("#averagerate")?.text()?.toDoubleOrNull()?.takeIf { votes != null }?.let {
            val stars = it.roundToInt().coerceIn(0, 5)
            "${"★".repeat(stars)}${"☆".repeat(5 - stars)} $it ($votes votos)"
        }
        val year = document.selectFirst("div.post-status a[href*=/ano/]")?.textOrNull()
        // Titles with a synopsis open with a generated summary paragraph, then a "<title> – Sinopse" heading
        val summary = document.selectFirst("div.summary__content")
        val synopsis = (summary?.selectFirst("> h3")?.nextElementSiblings() ?: summary?.children())
            ?.eachText()?.filter(String::isNotBlank)?.joinToString("\n\n")?.ifEmpty { null }
        val altNames = document.selectFirst(altNameSelector)?.ownTextOrNull()?.takeUnless(::isUpdating)
        description = listOfNotNull(
            listOfNotNull(rating, year).joinToString(" · ").ifEmpty { null },
            synopsis,
            altNames?.let { "${intl["alt_names_heading"]} $it" },
        ).joinToString("\n\n").ifEmpty { null }
    }

    override suspend fun fetchChapters(mangaPath: String, id: String, mangaPage: Document?): List<SChapter> {
        val chapters = super.fetchChapters(mangaPath, id, mangaPage)
        if (!preferences.getBoolean(HIDE_REUPLOADS_PREF, false)) return chapters
        // Re-uploads get the same name and a "_N" slug suffix
        return chapters.groupBy { it.name }.values.map { uploads ->
            uploads.firstOrNull { !REUPLOAD_SLUG_REGEX.containsMatchIn(it.url) } ?: uploads.first()
        }
    }

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(searchUrlBuilder(1, "").build(), Fetch.DOCUMENT).asJsoup()
        return document.select("form.search-advanced-form input[name=genre[]]").map { input ->
            val slug = input.attr("value")
            GenreRoute(input.parent()!!.text(), slug, "/genero/$slug/")
        }.toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = data?.parseAs<List<GenreRoute>>().orEmpty()
        return FilterList(
            buildList {
                add(TextFilter(intl["author_filter_title"], "author"))
                add(TextFilter(intl["artist_filter_title"], "artist"))
                add(TextFilter(intl["year_filter_title"], "release"))
                add(CheckBoxGroup(intl["status_filter_title"], "status[]", statusFilterOptions))
                add(SelectFilter(intl["order_by_filter_title"], "m_orderby", orderByFilterOptions))
                if (genres.isNotEmpty()) {
                    add(Filter.Separator())
                    add(SelectFilter(intl["genre_condition_filter_title"], "op", genreConditionFilterOptions))
                    add(CheckBoxGroup(intl["genre_filter_title"], "genre[]", genres.map { it.name to it.slug }))
                }
            },
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_REUPLOADS_PREF
            title = "Ocultar capítulos repetidos"
            summary = "Mostra um envio por capítulo quando o site lista o mesmo capítulo mais de uma vez"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_REUPLOADS_PREF = "pref_hide_reuploads"
        private val REUPLOAD_SLUG_REGEX = Regex("""_\d+$""")
    }
}
