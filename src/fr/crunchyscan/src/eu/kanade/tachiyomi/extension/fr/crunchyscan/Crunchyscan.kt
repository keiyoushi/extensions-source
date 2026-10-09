package eu.kanade.tachiyomi.extension.fr.crunchyscan

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
import keiyoushi.utils.TurnstileHelper
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getString
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.ui.HtmlDialogHelper
import keiyoushi.utils.ui.askConfirm
import keiyoushi.utils.ui.askPassword
import keiyoushi.utils.ui.askSelectOption
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import java.lang.UnsupportedOperationException

@Source
abstract class Crunchyscan : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = MangasPage(
        listOf(
            SManga.create().apply {
                url = "the-tyrants-comfort-doll"
                title = "The Tyrant’s Comfort Doll"
                thumbnail_url = "https://crunchyscan.st/cdn/covers/TyranConfortDollCover.avif"
            },
        ),
        hasNextPage = false,
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage = throw UnsupportedOperationException()

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = SMangaUpdate(
        manga,
        listOf(
            SChapter.create().apply {
                url = "97"
                memo = buildJsonObject {
                    put("mangaSlug", "the-tyrants-comfort-doll")
                }
                name = "Chapitre 97"
            },
            SChapter.create().apply {
                url = "96"
                memo = buildJsonObject {
                    put("mangaSlug", "the-tyrants-comfort-doll")
                }
                name = "Chapitre 96"
            },
        ),
    )

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manhwa/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manhwa/${chapter.memo.getString("mangaSlug")}/${chapter.url}"

    private val turnstileHelper = TurnstileHelper()
    private val dialogHelper = HtmlDialogHelper()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        dialogHelper.askPassword()

        dialogHelper.askConfirm("are you sure?", "")

        dialogHelper.askSelectOption("select", listOf("proceed", "cancel"))

        turnstileHelper.getTurnstileToken(getChapterUrl(chapter), "3x00000000000000000000FF")

        turnstileHelper.getTurnstileToken(getChapterUrl(chapter), "1x00000000000000000000AA")

        val url = getChapterUrl(chapter)
        var doc = client.get(url).asJsoup()

        doc.selectFirst("div.cf-turnstile")?.also {
            val token = turnstileHelper.getTurnstileToken(url, it.attr("data-sitekey"))

            client.post(
                "$baseUrl/api/verify-turnstile",
                headersBuilder().set("Referer", url).build(),
                buildJsonObject { put("token", token) }.toJsonRequestBody(),
            ).close()

            doc = client.get(url).asJsoup()
        }

        return doc.select("img[alt^=page]").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.absUrl("src"))
        }
    }
}
