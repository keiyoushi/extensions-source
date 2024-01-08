package eu.kanade.tachiyomi.extension.ja.mangacross

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import org.jsoup.Jsoup
import java.text.DateFormat.getDateTimeInstance
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class MCComicList(
    val comics: List<MCComic>,
//  Note: Pagination does not work. Pages after 1 return nothing.
//  val current_count: Int,
//  val current_page: Int?,
//  val total_count: Int,
//  val total_pages: Int,
)

// Useless fields are omitted while interesting ones are commented
@Serializable
data class MCComic(
    val dir_name: String,
    val title: String,
    val author: String,
    val comic_category: MCComicCategory? = null,
    val comic_tags: List<MCComicGenre>,
    val image_double_url: String, // horizontal
    val list_image_double_url: String, // square
//  val restricted: Boolean,  // is NSFW
//  Details below
    val outline: String? = null,
    val episodes: List<MCEpisode>? = null,
//  val books: List<MCBook>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/comics/$dir_name"
        title = this@MCComic.title
        author = this@MCComic.author
        description = getDescription()
        genre = getGenre()
        status = getStatus()
        thumbnail_url = list_image_double_url
    }

    fun toSChapterList() = episodes!!
        // .filter { it.status == "public" }  // preserve private chapters in case user downloaded before
        .map { it.toSChapter("/comics/$dir_name") }

    private fun getDescription() = listOfNotNull(
        episodes?.firstOrNull()?.getNextDatePrefix(),
        outline?.stripHtml(),
    ).joinToString("\n")

    private fun getGenre() = listOfNotNull(
        comic_category?.display_name,
        comic_tags.joinToString(", ") { it.name },
    ).joinToString(", ")

    private fun getStatus() = when {
        episodes?.firstOrNull()?.episode_next_date.isNullOrEmpty() -> SManga.UNKNOWN
        else -> SManga.ONGOING
    }

    private fun String.stripHtml() = Jsoup.parseBodyFragment(this).text()
}

sealed class MCComicTag

@Serializable
data class MCComicCategory(val name: String, val display_name: String) : MCComicTag()

@Serializable
data class MCComicGenre(val name: String) : MCComicTag()

@Serializable
data class MCComicDetails(val comic: MCComic)

@Serializable
data class MCEpisodeList(
    val episodes: List<MCEpisode>,
//  Note: Pagination works.
    val current_count: Int,
    val current_page: Int,
    val total_count: Int,
    val total_pages: Int,
)

@Serializable
data class MCEpisode(
//  val id: Long,
    val volume: String,
    val sort_volume: Int,
    val title: String,
    val publish_start: String, // all dates are in ISO time format
    val publish_end: String?,
//  Note: AFAIK these dates are always identical to those above.
//  val member_publish_start: String,
//  val member_publish_end: String?,
    val status: String, // public or private
//  val page_url: String,
//  val list_image_double_url: String,
    val episode_next_date: String?,
    val next_date_customize_text: String?,
    val comic: MCComic? = null, // in latest
) {
    fun toSChapter(urlPrefix: String) = SChapter.create().apply {
        url = "$urlPrefix/$sort_volume/viewer.json"
        val prefix = if (status == "public") "" else "🔒 "
        name = "$prefix$volume $title"
        // milliseconds are always 000
        date_upload = JST_FORMAT_LIST.parseJST(publish_start)!!.time
        // show end date in scanlator field
        scanlator = publish_end?.let { "~" + LOCAL_FORMAT_LIST.format(JST_FORMAT_LIST.parseJST(it)!!) }
    }

    fun getNextDatePrefix(): String? = when {
        !episode_next_date.isNullOrEmpty() -> {
            val date = JST_FORMAT_DESC.parseJST(episode_next_date)!!.apply {
                time += 10 * 3600 * 1000 // 10 am JST
            }
            "【Next: ${LOCAL_FORMAT_DESC.format(date)}】"
        }
        !next_date_customize_text.isNullOrEmpty() -> "【$next_date_customize_text】"
        else -> null
    }

    companion object {
        // for thread-safety
        private val JST_FORMAT_DESC = getJSTFormat()
        private val JST_FORMAT_LIST = getJSTFormat()
        private val LOCAL_FORMAT_DESC = getDateTimeInstance()
        private val LOCAL_FORMAT_LIST = getDateTimeInstance()

        private fun SimpleDateFormat.parseJST(date: String) = parse(date.removeSuffix("+09:00"))
        private fun getJSTFormat() =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("GMT+09:00")
            }
    }
}

@Serializable
data class MCBook(
    val cover_url: String, // resolution is too low
)

@Serializable
data class MCViewer(
//  val sort_volume: Int,
//  val created_at: String,
//  val updated_at: String,
//  val volume: String,
//  val title: String,
//  val page_count: Int,
//  episode_viewer_setting: { page_direction: "horizontal" }
    val episode_pages: List<MCEpisodePage>,
)

@Serializable
data class MCEpisodePage(
//  val order_index: Int,
    val image: MCImage,
//  val is_spread_start_page: Boolean,
)

@Serializable
data class MCImage(
//  val pc_url: String,  // has highest resolution but is upscaled from original, thus unnecessary
//  val sp_url: String,
//  val thumbnail_url: String,
    val original_url: String,
//  {pc,sp,thumbnail,original}_geometry: { width: Int, height: Int }
)

@Serializable
data class MCMenu(
    val comic_categories: List<MCComicCategory>,
    val comic_tags: List<MCComicGenre>,
) {
    fun toFilterList(): List<Pair<String, MCComicTag>> =
        comic_categories.map { Pair(it.display_name, it) } + comic_tags.map { Pair(it.name, it) }
}
