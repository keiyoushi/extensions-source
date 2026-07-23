package eu.kanade.tachiyomi.extension.all.hentai3

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class Hentai3 :
    KeiSource(),
    ConfigurableSource {

    private val searchLang: String
        get() = when (lang) {
            "all" -> ""
            "en" -> "english"
            "ja" -> "japanese"
            "ko" -> "korean"
            "zh" -> "chinese"
            "mo" -> "mongolian"
            "es" -> "spanish"
            "pt" -> "Portuguese"
            "id" -> "indonesian"
            "jv" -> "javanese"
            "tl" -> "tagalog"
            "vi" -> "vietnamese"
            "th" -> "thai"
            "my" -> "burmese"
            "tr" -> "turkish"
            "ru" -> "russian"
            "uk" -> "ukrainian"
            "pl" -> "polish"
            "fi" -> "finnish"
            "de" -> "german"
            "it" -> "italian"
            "fr" -> "french"
            "nl" -> "dutch"
            "cs" -> "czech"
            "hu" -> "hungarian"
            "bg" -> "bulgarian"
            "is" -> "icelandic"
            "la" -> "latin"
            "ar" -> "arabic"
            else -> ""
        }

    private val prefs by getPreferencesLazy()

    // Popular + Latest
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = if (searchLang.isNotEmpty()) {
            "$baseUrl/language/$searchLang/${if (page > 1) page else ""}?sort=popular"
        } else {
            "$baseUrl/search?q=pages%3A>0&page=$page&sort=popular"
        }
        val doc = client.get(url).asJsoup()
        return parseMangasPage(doc)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (searchLang.isNotEmpty()) {
            "$baseUrl/language/$searchLang/$page"
        } else {
            "$baseUrl/search?q=pages%3A>0&page=$page"
        }
        val doc = client.get(url).asJsoup()
        return parseMangasPage(doc)
    }

    private fun parseMangasPage(doc: Document): MangasPage {
        val mangas = doc.select("a[href*=/d/]").map { element ->
            SManga.create().apply {
                title = element.selectFirst("div.title")!!.ownText().shortenTitle()
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("img:not([class])")!!.absUrl("src")
            }
        }
        val hasNextPage = doc.selectFirst("a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var sort = ""

        val tags = buildString {
            filters.forEach { filter ->
                when (filter) {
                    is SelectFilter -> sort = filter.getValue()

                    is TextFilter -> {
                        filter.state.split(",")
                            .asSequence()
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .forEach { rawTag ->
                                val tag = rawTag.lowercase()

                                if (tag.startsWith("-")) append("-")

                                append(filter.type)

                                if (filter.type == "page") {
                                    append(":$tag")
                                } else {
                                    append(":'")
                                    append(tag.removePrefix("-"))

                                    if (filter.specific.isNotEmpty()) {
                                        append(" (${filter.specific})")
                                    }
                                    append("' ")
                                }
                            }
                    }

                    else -> {}
                }
            }
        }

        val language = if (searchLang.isNotEmpty()) "language:$searchLang" else ""

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("q", "$query $language $tags")
            if (page > 1) addQueryParameter("page", page.toString())
            addQueryParameter("sort", sort)
        }.build()

        return parseMangasPage(client.get(url).asJsoup())
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    // Details + Chapters
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (url.pathSegments.getOrNull(0) != "d") return null

        return url.pathSegments.getOrNull(1)?.let { id ->
            parseMangaDetails(client.get("$baseUrl/d/$id").asJsoup())
        }
    }

    private fun parseMangaDetails(document: Document): SManga {
        fun String.capitalizeEach() = this.split(" ").joinToString(" ") { s ->
            s.replaceFirstChar { sr ->
                if (sr.isLowerCase()) sr.titlecase(Locale.getDefault()) else sr.toString()
            }
        }
        return SManga.create().apply {
            setUrlWithoutDomain(document.location())

            val authors = document.select("a[href*=/groups/]").eachText().joinToString()
            val artists = document.select("a[href*=/artists/]").eachText().joinToString()
            initialized = true

            title = document.select("h1").text().shortenTitle()
            author = authors.ifEmpty { artists }
            artist = artists.ifEmpty { authors }
            genre = document.select("a[href*=/tags/]").eachText().joinToString {
                val capitalized = it.capitalizeEach()
                if (capitalized.contains("male")) {
                    capitalized.replace("(female)", "♀").replace("(male)", "♂")
                } else {
                    "$capitalized ◊"
                }
            }

            description = buildString {
                document.select("a[href*=/characters/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Characters: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/series/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Series: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/groups/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Groups: ", it.capitalizeEach(), "\n\n")
                }
                document.select("a[href*=/language/]").eachText().joinToString().ifEmpty { null }?.let {
                    append("Languages: ", it.capitalizeEach(), "\n\n")
                }
                append(document.select("div.tag-container:contains(pages:)").text(), "\n")
            }
            thumbnail_url = document.selectFirst("img")!!.let { img ->
                img.attr("data-src").ifEmpty { img.absUrl("src") }
            }
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    private fun parseChapterList(doc: Document): List<SChapter> = listOf(
        SChapter.create().apply {
            name = "Chapter"
            setUrlWithoutDomain(doc.location())
            date_upload = dateFormat.tryParse(doc.select("time").attr("datetime"))
        },
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(doc), parseChapterList(doc))
    }

    // Related manga
    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return doc.select("#similar-content .doujin-col .doujin a.cover").mapNotNull { link ->
            SManga.create().apply {
                title = link.selectFirst(".title")!!.text()
                setUrlWithoutDomain(link.absUrl("href"))
                thumbnail_url = link.selectFirst("img")!!.let { img ->
                    img.attr("data-src").ifEmpty { img.absUrl("src") }
                }
            }
        }
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val doc = client.get(getChapterUrl(chapter)).asJsoup()
        return doc.select("img:not([class], [src*=thumb], [src*=cover])")
            .mapIndexed { index, image ->
                val imageUrl = image.absUrl("src")
                Page(index, imageUrl = imageUrl.replace(REMOVE_THUMB_REGEX, ""))
            }
    }

    // Preferences
    private fun String.shortenTitle() = if (displayFullTitle) {
        this
    } else {
        replace(SHORT_TITLE_REGEX, "").trim()
    }

    private val displayFullTitle
        get() = prefs.getBoolean("full_title", false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = "full_title"
            title = "Display full title"
        }.also(screen::addPreference)
    }

    companion object {
        private val SHORT_TITLE_REGEX = Regex("""(\[[^]]*]|[({][^)}]*[)}])""")
        private val REMOVE_THUMB_REGEX = Regex("t(?=\\.)")
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.ROOT)
    }
}
