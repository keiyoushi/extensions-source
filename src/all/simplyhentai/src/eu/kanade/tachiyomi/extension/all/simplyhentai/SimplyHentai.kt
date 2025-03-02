package eu.kanade.tachiyomi.extension.all.simplyhentai

import android.net.Uri
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

open class SimplyHentai(
    override val lang: String,
    private val langName: String,
) : ConfigurableSource, HttpSource() {

    override val name = "Simply Hentai"

    override val baseUrl = "https://www.simply-hentai.com"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override val versionId = 2

    private val apiUrl = "https://api.simply-hentai.com/v3"

    private val json: Json by injectLazy()

    private val preferences by getPreferencesLazy()

    override fun popularMangaRequest(page: Int) =
        Uri.parse("$apiUrl/tag/$langName").buildUpon().run {
            appendQueryParameter("type", "language")
            appendQueryParameter("page", page.toString())
            GET(build().toString(), headers)
        }

    override fun popularMangaParse(response: Response) =
        response.decode<SHList<SHDataAlbum>>().run {
            MangasPage(
                data.albums.map(SHObject::toSManga),
                pagination.next != null,
            )
        }

    override fun latestUpdatesRequest(page: Int) =
        Uri.parse("$apiUrl/tag/$langName").buildUpon().run {
            appendQueryParameter("type", "language")
            appendQueryParameter("page", page.toString())
            appendQueryParameter("sort", "newest")
            GET(build().toString(), headers)
        }

    override fun latestUpdatesParse(response: Response) =
        popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        Uri.parse("$apiUrl/search/complex").buildUpon().run {
            appendQueryParameter("query", query)
            appendQueryParameter("page", page.toString())
            appendQueryParameter("blacklist", blacklist)
            appendQueryParameter("filter[language][0]", langName.replaceFirstChar(Char::uppercase))
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        appendQueryParameter("sort", filter.orders[filter.state])
                    }
                    is SeriesFilter -> filter.value?.also {
                        appendQueryParameter("filter[series_title][0]", it)
                    }
                    is TagsFilter -> filter.value?.forEachIndexed { idx, tag ->
                        appendQueryParameter("filter[tags][$idx]", tag.trim())
                    }
                    is ArtistsFilter -> filter.value?.forEachIndexed { idx, tag ->
                        appendQueryParameter("filter[artists][$idx]", tag.trim())
                    }
                    is TranslatorsFilter -> filter.value?.forEachIndexed { idx, tag ->
                        appendQueryParameter("filter[translators][$idx]", tag.trim())
                    }
                    is CharactersFilter -> filter.value?.forEachIndexed { idx, tag ->
                        appendQueryParameter("filter[characters][$idx]", tag.trim())
                    }
                    else -> {}
                }
            }
            GET(build().toString(), headers)
        }

    override fun searchMangaParse(response: Response) =
        response.decode<SHList<List<SHWrapper>>>().run {
            MangasPage(
                data.map { it.`object`.toSManga() },
                pagination.next != null,
            )
        }

    override fun mangaDetailsRequest(manga: SManga) = chapterListRequest(manga)

    override fun mangaDetailsParse(response: Response) =
        SManga.create().apply {
            val album = response.decode<SHAlbum>().data
            url = album.path
            title = album.title
            description = buildString {
                if (!album.description.isNullOrEmpty()) {
                    append(album.description, "\n\n")
                }
                append("Series: ", album.series.title, "\n")
                album.characters.joinTo(this, prefix = "Characters: ") { it.title }
            }
            thumbnail_url = album.preview.sizes.thumb
            genre = album.tags.joinToString { it.title }
            artist = album.artists.joinToString { it.title }
            author = artist
            initialized = true
        }

    override fun chapterListRequest(manga: SManga) =
        Uri.parse("$apiUrl/manga").buildUpon().run {
            appendEncodedPath(manga.url.split('/')[2])
            GET(build().toString(), headers)
        }

    override fun chapterListParse(response: Response) =
        SChapter.create().apply {
            val album = response.decode<SHAlbum>().data
            name = "Chapter"
            url = "${album.path}/all-pages"
            scanlator = album.translators.joinToString { it.title }
            date_upload = dateFormat.parse(album.created_at)?.time ?: 0L
        }.let(::listOf)

    override fun pageListRequest(chapter: SChapter) =
        Uri.parse("$apiUrl/manga").buildUpon().run {
            appendEncodedPath(chapter.url.split('/')[2])
            appendEncodedPath("pages")
            GET(build().toString(), headers)
        }

    override fun pageListParse(response: Response) =
        response.decode<SHAlbumPages>().data.pages.map {
            Page(it.page_num, "", it.sizes.full)
        }

    override fun getFilterList() = FilterList(
        SortFilter(),
        SeriesFilter(),
        Note("tags"),
        TagsFilter(),
        Note("artists"),
        ArtistsFilter(),
        Note("translators"),
        TranslatorsFilter(),
        Note("characters"),
        CharactersFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "blacklist"
            title = "Blacklist"
            summary = "Separate multiple tags with commas (,)"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString("blacklist", newValue as String).commit()
            }
        }.let(screen::addPreference)
    }

    private inline val blacklist: String
        get() = preferences.getString("blacklist", "")!!

    private inline fun <reified T> Response.decode(): T =
        json.decodeFromStream(body.byteStream())

    override fun imageUrlParse(response: Response) =
        throw UnsupportedOperationException()

    companion object {
        private val dateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ROOT)
    }
}
