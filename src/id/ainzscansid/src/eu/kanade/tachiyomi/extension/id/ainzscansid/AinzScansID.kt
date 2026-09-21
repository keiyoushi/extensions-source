package eu.kanade.tachiyomi.extension.id.ainzscansid

import eu.kanade.tachiyomi.multisrc.loneseal.ChapterPagesResponseDto
import eu.kanade.tachiyomi.multisrc.loneseal.LoneSeal
import eu.kanade.tachiyomi.multisrc.loneseal.UrlLayout
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val adDomainRegex = """^999(?:-\d+)?\.jpe?g$""".toRegex()
private val adDonationRegex = """^997(?:-\d+)?\.jpe?g$""".toRegex()
private val adVotePreRegex = """^00\.0\.jpg$""".toRegex()
private val adReadOnRegex = """^00\.1\.jpg$""".toRegex()
private val adVotePostRegex = """^995\.jpg$""".toRegex()

@Source
abstract class AinzScansID : LoneSeal() {
    override val urlLayout = UrlLayout.LEGACY_COMIC
    override val includeProjectOnlyFilter = true
    override val overloadedGenres = super.overloadedGenres + setOf("adventure")

    override fun toPageList(dto: ChapterPagesResponseDto) = buildList {
        val pages = dto.chapter.pages
        pages.forEachIndexed { i, page ->
            val url = page.imageUrl.cleanUp()
            val filename = url.toHttpUrlOrNull()?.pathSegments?.lastOrNull()

            val isAd = when (i) {
                pages.lastIndex -> filename?.matches(adDomainRegex) == true
                pages.lastIndex - 2 -> filename?.matches(adDonationRegex) == true || filename?.matches(adVotePostRegex) == true
                0 -> filename?.matches(adVotePreRegex) == true
                1 -> filename?.matches(adReadOnRegex) == true
                else -> false
            }

            if (!isAd) {
                add(Page(i, imageUrl = url))
            }
        }
    }

    private fun String.cleanUp(): String {
        var url = if (startsWith("http")) this else "https://api.ainzscans01.com$this"

        // Fix for older chapters using Blogger/Googleusercontent compressed images
        if (url.contains("googleusercontent.com") || url.contains("bp.blogspot.com")) {
            url = url.replace(Regex("""=[swh]\d+[^/?]*($|\?)""", RegexOption.IGNORE_CASE), "=s0$1")
                .replace(Regex("""/[swh]\d+[^/]*/""", RegexOption.IGNORE_CASE), "/s0/")
        }

        // Clean up common CMS resizing query parameters
        url.toHttpUrlOrNull()?.let { httpUrl ->
            if (httpUrl.queryParameterNames.any { it == "w" || it == "width" || it == "resize" }) {
                url = httpUrl.newBuilder()
                    .removeAllQueryParameters("w")
                    .removeAllQueryParameters("width")
                    .removeAllQueryParameters("resize")
                    .build()
                    .toString()
            }
        }

        return url
    }
}
