package eu.kanade.tachiyomi.extension.ja.ganma

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.DateFormat.getDateTimeInstance
import java.util.Date

@Serializable
class Result<T>(val root: T)

// Manga
@Serializable
class Magazine(
    val id: String,
    val alias: String? = null,
    val title: String,
    val description: String? = null,
    val squareImage: File? = null,
//  val squareWithLogoImage: File? = null,
    val author: Author? = null,
    val newestStoryItem: Story? = null,
    val flags: Flags? = null,
    val announcement: Announcement? = null,
    val items: List<Story> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = "${alias!!}#$id"
        title = this@Magazine.title
        thumbnail_url = squareImage!!.url
    }

    fun toSMangaDetails() = toSManga().apply {
        author = this@Magazine.author?.penName
        val flagsText = flags?.toText()
        description = generateDescription(flagsText)
        status = when {
            flags?.isFinish == true -> SManga.COMPLETED
            !flagsText.isNullOrEmpty() -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        initialized = true
    }

    private fun generateDescription(flagsText: String?): String {
        val result = mutableListOf<String>()
        if (!flagsText.isNullOrEmpty()) result.add("Updates: $flagsText")
        if (announcement != null) result.add("Announcement: ${announcement.text}")
        if (description != null) result.add(description)
        return result.joinToString("\n\n")
    }

    fun getSChapterList(): List<SChapter> {
        val now = System.currentTimeMillis()
        return items.map {
            SChapter.create().apply {
                url = "${alias!!}#$id/${it.id ?: it.storyId}"
                name = buildString {
                    if (it.kind != "free") append("🔒 ")
                    append(it.title)
                    if (it.subtitle != null) append(' ').append(it.subtitle)
                }
                val time = it.releaseStart ?: -1
                date_upload = time
                if (time > now) scanlator = getDateTimeInstance().format(Date(time)) + '~'
            }
        }
    }
}

fun String.alias() = this.substringBefore('#')
fun String.mangaId() = this.substringAfter('#')
fun String.chapterDir(): Pair<String, String> =
    with(this.substringAfter('#')) {
        // this == [mangaId-UUID]/[chapterId-UUID]
        Pair(substring(0, 36), substring(37, 37 + 36))
    }

// Chapter
@Serializable
class Story(
    val id: String? = null,
    val storyId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val release: Long = 0,
    val releaseStart: Long? = null,
    val page: Directory? = null,
    val afterwordImage: File? = null,
    val kind: String? = null,
) {
    fun toPageList(): List<Page> {
        val result = page!!.toPageList()
        if (afterwordImage != null) {
            result.add(Page(result.size, imageUrl = afterwordImage.url))
        }
        return result
    }
}

@Serializable
class File(val url: String)

@Serializable
class Author(val penName: String? = null)

@Serializable
class Top(val boxes: List<Box>)

@Serializable
class Box(val panels: List<Magazine>)

@Serializable
class Flags(
    val isMonday: Boolean = false,
    val isTuesday: Boolean = false,
    val isWednesday: Boolean = false,
    val isThursday: Boolean = false,
    val isFriday: Boolean = false,
    val isSaturday: Boolean = false,
    val isSunday: Boolean = false,

    val isWeekly: Boolean = false,
    val isEveryOtherWeek: Boolean = false,
    val isThreeConsecutiveWeeks: Boolean = false,
    val isMonthly: Boolean = false,

    val isFinish: Boolean = false,
//  val isMGAward: Boolean = false,
//  val isNew: Boolean = false,
) {
    fun toText(): String {
        val result = mutableListOf<String>()
        val days = mutableListOf<String>()
        arrayOf(isWeekly, isEveryOtherWeek, isThreeConsecutiveWeeks, isMonthly)
            .forEachIndexed { i, value -> if (value) result.add(weekText[i]) }
        arrayOf(isMonday, isTuesday, isWednesday, isThursday, isFriday, isSaturday, isSunday)
            .forEachIndexed { i, value -> if (value) days.add(dayText[i] + "s") }
        if (days.size == 7) {
            result.add("every day")
        } else if (days.size != 0) {
            days[0] = "on " + days[0]
            result += days
        }
        return result.joinToString(", ")
    }

    companion object {
        private val weekText = arrayOf("every week", "every other week", "three weeks in a row", "every month")
        private val dayText = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    }
}

@Serializable
class Announcement(val text: String)

@Serializable
class Directory(
    val baseUrl: String,
    val token: String,
    val files: List<String>,
) {
    fun toPageList(): MutableList<Page> =
        files.mapIndexedTo(ArrayList(files.size + 1)) { i, file ->
            Page(i, imageUrl = "$baseUrl$file?$token")
        }
}

@Serializable
class AppStory(val pages: List<AppPage>) {
    fun toPageList(): List<Page> {
        val result = ArrayList<Page>(pages.size)
        pages.forEach {
            if (it.imageURL != null) {
                result.add(Page(result.size, imageUrl = it.imageURL.url))
            } else if (it.afterwordImageURL != null) {
                result.add(Page(result.size, imageUrl = it.afterwordImageURL.url))
            }
        }
        return result
    }
}

@Serializable
class AppPage(
    val imageURL: File? = null,
    val afterwordImageURL: File? = null,
)

// Please keep the data private to support the site,
// otherwise they might change their APIs.
@Serializable
class Metadata(
    val userAgent: String,
    val baseUrl: String,
    val tokenUrl: String,
    val tokenField1: String,
    val tokenField2: String,
    val sessionUrl: String,
    val cookieName: String,
    val magazineUrl: String,
    val storyUrl: String,
)

@Serializable
class Session(val expire: Long)
