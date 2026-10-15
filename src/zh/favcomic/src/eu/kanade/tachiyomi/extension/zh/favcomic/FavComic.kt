package eu.kanade.tachiyomi.extension.zh.favcomic

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
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Element

@Serializable
private class LoginResultDto(
    val result: String? = null,
    val msg: String? = null,
)

@Source
abstract class FavComic :
    KeiSource(),
    ConfigurableSource {

    private val pref by getPreferencesLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        preferencesInternal(screen.context, pref).forEach(screen::addPreference)
    }

    override fun OkHttpClient.Builder.configureClient() = apply { addInterceptor(ImageDecryptInterceptor()) }

    private suspend fun login(): Boolean {
        val username = pref.getString(PREF_USERNAME, "").orEmpty()
        val password = pref.getString(PREF_PASSWORD, "").orEmpty()
        if (username.isBlank() || password.isBlank()) {
            return false
        }

        val formBody = FormBody.Builder()
            .add("loginName", username)
            .add("password", password)
            .build()

        val headers = headers.newBuilder()
            .set("Referer", "$baseUrl/login")
            .build()

        val dto = client.post("$baseUrl/login", headers = headers, body = formBody).parseAs<LoginResultDto>()
        if (dto.result == "success") {
            return true
        }
        if (!dto.msg.isNullOrBlank()) {
            throw Exception("自动登录失败: ${dto.msg}")
        }
        return false
    }

    private fun mangasPageParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val mangas = doc.select(".cover_box > a").map { a ->
            val img = a.selectFirst(".cover")!!
            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                thumbnail_url = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}"
                title = a.attr("title")
            }
        }
        val e = doc.selectFirst(".pagination_box > .content_box > div:nth-last-child(2) > a")!!
        return MangasPage(mangas, !e.hasClass("active"))
    }

    // Popular

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/rank".toHttpUrl().newBuilder()
            .addQueryParameter("range", pref.getString(PREF_RANK_TYPE, "1"))
            .addQueryParameter("comicType", pref.getString(PREF_MANGA_TYPE, "boy-1")!!.substringAfter('-'))
            .addQueryParameter("vip", "0")
            .build()
        val mangas = client.get(url).asJsoup().select(".rank_item > a").map { a ->
            val img = a.selectFirst(".cover > img")!!
            val authors = a.select(".author a").joinToString { it.text() }
                .ifEmpty { a.selectFirst(".author")?.text().orEmpty() }
            SManga.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                thumbnail_url = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}"
                title = img.attr("alt")
                author = authors
                description = a.selectFirst(".brief")!!.text()
            }
        }
        return MangasPage(mangas, false)
    }

    // Updates

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/${pref.getString(PREF_MANGA_TYPE, "boy-1")!!.substringBefore('-')}?page=$page"
        return mangasPageParse(client.get(url))
    }

    // Search

    override fun getFilterList(data: JsonElement?) = buildFilterList()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangaTypeFilter = filters.firstInstance<MangaTypeFilter>()
        val pathSegment = if (query.isNotBlank()) "search" else mangaTypeFilter.toString()
        val url = baseUrl.toHttpUrl().newBuilder().addPathSegment(pathSegment)
            .addQueryParameter("keyword", query)
            .addQueryParameter("origin", filters[2].toString())
            .addQueryParameter("finished", filters[3].toString())
            .addQueryParameter("free", filters[4].toString())
            .addQueryParameter("sort", filters[5].toString())
            .addQueryParameter("page", page.toString())
        filters.firstInstance<TagGroup>().getTag(mangaTypeFilter.state)?.run { url.addQueryParameter("tag", this) }
        return mangasPageParse(client.get(url.build()))
    }

    // Manga & Chapters

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(baseUrl + manga.url).asJsoup()

        val img = doc.selectFirst(".comic_cover_box > .flex_box > img")!!
        val note = doc.selectFirst(".translation_agency_box")?.text()
        val baseIntro = doc.selectFirst(".intro_box > .txt")!!.text().substringAfter("作品介绍：")
        val authors = doc.selectFirst(".author")?.select("a")?.joinToString { it.text() }
            ?.ifEmpty { null } ?: doc.selectFirst(".author")?.text().orEmpty()

        val sManga = SManga.create().apply {
            setUrlWithoutDomain(doc.location())
            title = doc.selectFirst(".comic_title")!!.text()
            thumbnail_url = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}"
            author = authors
            description = baseIntro + (note?.let { "\n\n*译者/机构：$it*" } ?: "")
            status = when (doc.selectFirst(".state_box > span:nth-of-type(2)")?.text()) {
                "连载中" -> SManga.ONGOING
                "完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = doc.select(".tag_box a").joinToString(transform = Element::text)
        }

        val sChapters = doc.select(".catalog_box a").map { a ->
            val price = a.selectFirst("span:last-child")?.text()?.toFloatOrNull()
            SChapter.create().apply {
                setUrlWithoutDomain(a.absUrl("href"))
                name = (price?.let { "\uD83E\uDE99 " } ?: "") + a.selectFirst(".title")!!.text()
                scanlator = price?.let { "￥$price" }
            }
        }.reversed()

        return SMangaUpdate(sManga, sChapters)
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var doc = client.get(baseUrl + chapter.url).asJsoup()
        var code = doc.selectFirst(".comic_chapter_box")?.attr("code")
        if (code == "1") {
            if (login()) {
                doc = client.get(baseUrl + chapter.url).asJsoup()
                code = doc.selectFirst(".comic_chapter_box")?.attr("code")
            }
        }
        when (code) {
            "1" -> throw Exception("此话需在扩展设置中配置账号密码自动登录，或在 WebView 中登录才能看")
            "3" -> throw Exception("金币不足，请充值")
            "4" -> throw Exception("请在 WebView 中付费解锁此话")
            "444" -> throw Exception("免费额度已用完，明天零点重置")
        }
        return doc.select("#content > img").mapIndexed { i, img ->
            Page(i, imageUrl = "${img.absUrl("data-src")}#${img.hasClass("encrypted-image")}")
        }
    }
}
