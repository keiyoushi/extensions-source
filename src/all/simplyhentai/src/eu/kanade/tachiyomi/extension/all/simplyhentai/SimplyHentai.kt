package eu.kanade.tachiyomi.extension.all.simplyhentai

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Instant

@Source
abstract class SimplyHentai :
    KeiSource(),
    ConfigurableSource {

    private val langName: String
        get() = when (lang) {
            "en" -> "english"
            "ja" -> "japanese"
            "zh" -> "chinese"
            "ko" -> "korean"
            "es" -> "spanish"
            "ru" -> "russian"
            "fr" -> "french"
            "de" -> "german"
            "it" -> "italian"
            "pl" -> "polish"
            else -> lang
        }

    private val apiUrl = "https://api.simply-hentai.com/v3"

    private val preferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage = getLanguageList(page, null)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getLanguageList(page, "newest")

    private suspend fun getLanguageList(page: Int, sort: String?): MangasPage {
        val url = "$apiUrl/tag/$langName".toHttpUrl().newBuilder().apply {
            addQueryParameter("type", "language")
            addQueryParameter("page", page.toString())
            sort?.let { addQueryParameter("sort", it) }
        }.build()

        return client.get(url).parseAs<SHList<SHDataAlbum>>().run {
            MangasPage(
                data.albums.map(SHObject::toSManga),
                pagination.next != null,
            )
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/search/complex".toHttpUrl().newBuilder().apply {
            addQueryParameter("query", query)
            addQueryParameter("page", page.toString())
            addQueryParameter("blacklist", blacklist)
            addQueryParameter("filter[language][0]", langName.replaceFirstChar(Char::uppercase))
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        addQueryParameter("sort", filter.orders[filter.state])
                    }

                    is SeriesFilter -> filter.value?.also {
                        addQueryParameter("filter[series_title][0]", it)
                    }

                    is TagsFilter -> filter.value?.forEachIndexed { idx, tag ->
                        addQueryParameter("filter[tags][$idx]", tag.trim())
                    }

                    is ArtistsFilter -> filter.value?.forEachIndexed { idx, tag ->
                        addQueryParameter("filter[artists][$idx]", tag.trim())
                    }

                    is TranslatorsFilter -> filter.value?.forEachIndexed { idx, tag ->
                        addQueryParameter("filter[translators][$idx]", tag.trim())
                    }

                    is CharactersFilter -> filter.value?.forEachIndexed { idx, tag ->
                        addQueryParameter("filter[characters][$idx]", tag.trim())
                    }

                    else -> {}
                }
            }
        }.build()

        return client.get(url).parseAs<SHList<List<SHWrapper>>>().run {
            MangasPage(
                data.map { it.`object`.toSManga() },
                pagination.next != null,
            )
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val album = client.get("$apiUrl/manga/${manga.url.split('/')[2]}").parseAs<SHAlbum>().data

        val updatedManga = manga.apply {
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
        }

        val chapter = SChapter.create().apply {
            name = "Chapter"
            url = "${album.path}/all-pages"
            scanlator = album.translators.joinToString { it.title }
            date_upload = Instant.tryParse(album.createdAt)
        }

        return SMangaUpdate(updatedManga, listOf(chapter))
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> = client.get("$apiUrl/manga/${chapter.url.split('/')[2]}/pages")
        .parseAs<SHAlbumPages>().data.pages.map {
            Page(it.pageNum, "", it.sizes.full)
        }

    override fun getFilterList(data: JsonElement?) = FilterList(
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
}
