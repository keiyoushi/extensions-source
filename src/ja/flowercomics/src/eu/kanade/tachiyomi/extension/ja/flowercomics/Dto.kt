package eu.kanade.tachiyomi.extension.ja.flowercomics

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
class RankingBlock(
    val rankingTypeName: String,
    val titles: List<Entries>,
)

@Serializable
class LatestData(
    val weekdays: Map<String, List<Entries>>,
)

@Serializable
class Entries(
    private val id: Int,
    private val thumbnail: Thumbnail?,
    private val name: String,
) {
    fun toSManga(): SManga = SManga.create().apply {
        url = id.toString()
        title = name
        thumbnail_url = thumbnail?.src
    }
}

@Serializable
class Thumbnail(
    val src: String?,
)

@Serializable
class EntryChapters(
    private val earlyChapters: List<EpisodeEntry>,
    private val omittedMiddleChapters: List<EpisodeEntry>,
    private val latestChapters: List<EpisodeEntry>,
) {
    val chapters: List<EpisodeEntry>
        get() = earlyChapters + omittedMiddleChapters + latestChapters
}

@Serializable
class EpisodeEntry(
    private val chapterType: Int,
    private val id: Int,
    private val subTitle: String?,
    private val title: String,
    private val updated: String?,
) {
    /**
     * 4 = Coins only,
     * 3 = Points and Coins,
     * 2 = Ticket,
     * 0 = Free/Unlocked
     */
    val isLocked: Boolean
        get() = (chapterType == 4 || chapterType == 3 || chapterType == 2)

    fun toSChapter() = SChapter.create().apply {
        val lock = if (isLocked) "🔒 " else ""
        url = id.toString()
        name = lock + title + subTitle
        date_upload = dateFormat.tryParseDate(updated)
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT)

@Serializable
class PageEntry(
    val src: String,
    val crypto: Crypto?,
)

@Serializable
class Crypto(
    val iv: String,
    val key: String,
)
