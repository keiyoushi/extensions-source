package eu.kanade.tachiyomi.extension.en.hentaitnt

import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getStringOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@Source
abstract class HentaiTnT :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/recommended" + (if (page > 1) "/page/$page" else "")).asJsoup()
        return mangaListParse(document)
    }

    private fun mangaListParse(document: Document): MangasPage {
        val mangas = document.select(".comic-card a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                title = element.attr("title")
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("a[title=Next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/latest-updates" + (if (page > 1) "/page/$page" else "")).asJsoup()
        return mangaListParse(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            } else {
                val genreFilter = filters.firstInstanceOrNull<Filters>()
                if (genreFilter != null) {
                    val genreId = genreFilter.selectedId
                    if (genreId.isNotEmpty()) {
                        addPathSegment("genre")
                        addPathSegment(genreId)
                    }
                }
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        val document = client.get(url).asJsoup()
        return mangaListParse(document)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val mangaId = manga.memo.getStringOrNull("id")

        if (!fetchChapters) {
            val updatedManga = if (fetchDetails) fetchMangaDetails(manga) else manga
            return SMangaUpdate(updatedManga, chapters)
        }

        if (mangaId == null) {
            val updatedManga = fetchMangaDetails(manga)
            val mangaId = updatedManga.memo.getStringOrNull("id")
                ?: throw Exception("Failed to get chapter id")
            val updatedChapters = fetchChapters(mangaId)
            return SMangaUpdate(updatedManga, updatedChapters)
        }

        if (!fetchDetails) {
            return SMangaUpdate(manga, fetchChapters(mangaId))
        }

        return coroutineScope {
            // `mangaId` is stale but stable, requesting both at the same time for performance
            val mangaDeferred = async { fetchMangaDetails(manga) }
            val chaptersDeferred = async { fetchChapters(mangaId) }
            SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
        }
    }

    private suspend fun fetchMangaDetails(manga: SManga): SManga {
        val detailsDocument = client.get(getMangaUrl(manga)).asJsoup()
        return SManga.create().apply {
            author = detailsDocument.selectFirst("i[title=Artists] + span a")?.text()
            description = detailsDocument.selectFirst("#synopsisText")?.text()
            genre = detailsDocument.select(".genre-item").joinToString { it.text() }
            status = when (detailsDocument.selectFirst("span:has(i[title=Status])")?.text()?.lowercase()) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }

            val mangaId = detailsDocument.selectFirst("#post_manga_id")?.attr("value")
            memo = buildJsonObject {
                put("id", mangaId)
            }
        }
    }

    private suspend fun fetchChapters(mangaId: String): List<SChapter> {
        val form = FormBody.Builder()
            .add("action", "baka_ajax")
            .add("type", "load_chapters_paginated")
            .add("parent_id", mangaId)
            .add("per_page", "10000")
            .add("order", "newest_first")
            .build()

        val dto = client.post("$baseUrl/wp-admin/admin-ajax.php", body = form).parseAs<Dto>()
        val chapterDoc = Jsoup.parseBodyFragment(dto.data.html, baseUrl)

        return chapterDoc.select(".comic-card").mapNotNull { element ->
            val link = element.selectFirst("a") ?: return@mapNotNull null
            val isVip = element.selectFirst(".fa-crown") != null

            if (isVip && preferences.getBoolean(HIDE_VIP_PREF, false)) {
                return@mapNotNull null
            }

            SChapter.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                name = (if (isVip) "🔒 " else "") + link.attr("title")
            }
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".page-image").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Ignored if using text search"),
        Filters(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = HIDE_VIP_PREF
            title = "Hide VIP chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_VIP_PREF = "hide_vip_chapters"
    }
}
