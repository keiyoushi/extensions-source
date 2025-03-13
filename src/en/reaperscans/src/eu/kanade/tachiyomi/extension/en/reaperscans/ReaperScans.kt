package eu.kanade.tachiyomi.extension.en.reaperscans

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms
import eu.kanade.tachiyomi.multisrc.heancms.SortProperty
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ReaperScans : HeanCms("Reaper Scans", "https://reaperscans.com", "en") {

    override val versionId = 3

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override val useNewChapterEndpoint = true
    override val useNewQueryEndpoint = true
    override val enableLogin = true
    override val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { this.timeZone = TimeZone.getTimeZone("UTC") }
    override val cdnUrl = "https://media.reaperscans.com/file/4SRBHm"

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/query".toHttpUrl().newBuilder()
            .addQueryParameter("query_string", "")
            .addQueryParameter(if (useNewQueryEndpoint) "status" else "series_status", "All")
            .addQueryParameter("order", "desc")
            .addQueryParameter("orderBy", "updated_at")
            .addQueryParameter("series_type", "Comic")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", "12")
            .addQueryParameter("tags_ids", "[]")
            .addQueryParameter("adult", "true")

        return GET(url.build(), headers)
    }

    override fun chapterListRequest(manga: SManga): Request = GET(
        "$apiUrl/chapters/".toHttpUrl().newBuilder().apply {
            val mangaUrl = (baseUrl + manga.url).toHttpUrl()
            addPathSegment(mangaUrl.fragment!!)
            addQueryParameter("page", "1")
            addQueryParameter("perPage", "1000")
            fragment(mangaUrl.pathSegments.last())
            // not needed. just added to be authentic
            addQueryParameter("query", "")
            addQueryParameter("order", "desc")
        }.build(),
        headers,
    )

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ReaperPagePayloadDto>()

        if (result.isPaywalled() && result.chapter.chapterData == null) {
            throw Exception(intl["paid_chapter_error"])
        }

        return if (useNewChapterEndpoint) {
            result.chapter.chapterData?.images().orEmpty().mapIndexed { i, img ->
                Page(i, imageUrl = img.toAbsoluteUrl())
            }
        } else {
            result.data.orEmpty().mapIndexed { i, img ->
                Page(i, imageUrl = img.toAbsoluteUrl())
            }
        }
    }

    override fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty(intl["sort_by_title"], "title"),
        SortProperty(intl["sort_by_views"], "total_views"),
        SortProperty(intl["sort_by_latest"], "updated_at"),
        SortProperty(intl["sort_by_created_at"], "created_at"),
    )
}
