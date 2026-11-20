package eu.kanade.tachiyomi.extension.en.maidsamamangaonline

import eu.kanade.tachiyomi.multisrc.solomanga.SoloManga
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source

@Source
abstract class MaidSamaMangaOnline : SoloManga() {

    override val mangaTitle = "Maid Sama"

    override val chapterSlug = "maid-sama-chapter-"

    override val chapterRegex = Regex("maid-sama-chapter-(.+?)/?$")

    override val imageHosts = listOf("img.maid-sama.com")

    private val specialRegex = Regex("""oneshot-special-(\d+)""")

    override fun chapterFromMatch(href: String, match: MatchResult): SChapter = SChapter.create().apply {
        val slug = match.groupValues[1]
        setUrlWithoutDomain(href)
        name = chapterName(slug)
        chapter_number = chapterNumber(slug)
    }

    private fun chapterName(slug: String): String {
        val special = specialRegex.find(slug)
        if (special != null) return "Oneshot Special ${special.groupValues[1]}"
        return "Chapter " + slug.replace("-", ".")
    }

    private fun chapterNumber(slug: String): Float {
        val special = specialRegex.find(slug)
        if (special != null) return 86f + special.groupValues[1].toFloat() / 100f
        var number = 0f
        var divisor = 1f
        slug.split("-").forEach { part ->
            number += (part.toFloatOrNull() ?: 0f) / divisor
            divisor *= 10f
        }
        return number
    }
}
