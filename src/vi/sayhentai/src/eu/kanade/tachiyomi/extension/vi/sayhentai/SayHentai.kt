package eu.kanade.tachiyomi.extension.vi.sayhentai

import eu.kanade.tachiyomi.multisrc.manhwaz.ManhwaZ
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.jsonInstance
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class SayHentai : ManhwaZ() {

    override val mangaDetailsAuthorHeading = "Tác giả"

    override val mangaDetailsStatusHeading = "Trạng thái"

    override fun popularMangaSelector() = "#slide-top > .item:contains(a)"

    override fun genreListSelector(): String = "ul.genres-grid li a"

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get("$baseUrl/genre").asJsoup()
        val genres = document.select(genreListSelector()).map {
            SelectOption(
                it.select(".genre-meta .name").text(),
                it.absUrl("href").toHttpUrl().encodedPath.removePrefix("/"),
            )
        }
        return jsonInstance.encodeToJsonElement(genres)
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        if (!fetchChapters) {
            return super.fetchMangaUpdate(manga, chapters, fetchDetails, false)
        }

        val response = client.get(getMangaUrl(manga))
        val document = response.asJsoup()
        val chapterList = parseChapterList(document).toMutableList()

        val moreChaptersUrl = document
            .selectFirst(".c-chapter-readmore")
            ?.attr("abs:data-ajax-url")
            ?.takeIf(String::isNotBlank)

        if (moreChaptersUrl != null) {
            val moreResponse = client.get(moreChaptersUrl)
            chapterList += parseChapterList(moreResponse.asJsoup())
        }

        return SMangaUpdate(
            manga = parseMangaDetails(document),
            chapters = chapterList,
        )
    }
}
