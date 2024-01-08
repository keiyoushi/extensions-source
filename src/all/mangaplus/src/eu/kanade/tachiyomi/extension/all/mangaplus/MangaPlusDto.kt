package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaPlusResponse(
    val success: SuccessResult? = null,
    val error: ErrorResult? = null,
)

@Serializable
data class ErrorResult(val popups: List<Popup> = emptyList()) {

    fun langPopup(lang: Language): Popup? =
        popups.firstOrNull { it.language == lang }
}

@Serializable
data class Popup(
    val subject: String,
    val body: String,
    val language: Language? = Language.ENGLISH,
)

@Serializable
data class SuccessResult(
    val isFeaturedUpdated: Boolean? = false,
    val titleRankingView: TitleRankingView? = null,
    val titleDetailView: TitleDetailView? = null,
    val mangaViewer: MangaViewer? = null,
    val allTitlesViewV2: AllTitlesViewV2? = null,
    val webHomeViewV3: WebHomeViewV3? = null,
)

@Serializable
data class TitleRankingView(val titles: List<Title> = emptyList())

@Serializable
data class AllTitlesViewV2(
    @SerialName("AllTitlesGroup") val allTitlesGroup: List<AllTitlesGroup> = emptyList(),
)

@Serializable
data class AllTitlesGroup(
    val theTitle: String,
    val titles: List<Title> = emptyList(),
)

@Serializable
data class WebHomeViewV3(val groups: List<UpdatedTitleV2Group> = emptyList())

@Serializable
data class TitleDetailView(
    val title: Title,
    val titleImageUrl: String,
    val overview: String? = null,
    val nextTimeStamp: Int = 0,
    val viewingPeriodDescription: String = "",
    val nonAppearanceInfo: String = "",
    val chapterListGroup: List<ChapterListGroup> = emptyList(),
    val isSimulReleased: Boolean = false,
    val rating: Rating = Rating.ALL_AGES,
    val chaptersDescending: Boolean = true,
    val titleLabels: TitleLabels,
    val label: Label? = Label(LabelCode.WEEKLY_SHOUNEN_JUMP),
) {

    val chapterList: List<Chapter> by lazy {
        // Doesn't include `midChapterList` by design as their site API returns it
        // just for visual representation to redirect users to their app. The extension
        // intends to allow users to read only what they can already read in their site.
        chapterListGroup.flatMap { it.firstChapterList + it.lastChapterList }
    }

    private val isWebtoon: Boolean
        get() = chapterList.isNotEmpty() && chapterList.all(Chapter::isVerticalOnly)

    private val isOneShot: Boolean
        get() = chapterList.size == 1 && chapterList.firstOrNull()
            ?.name?.equals("one-shot", true) == true

    private val isReEdition: Boolean
        get() = viewingPeriodDescription.contains(REEDITION_REGEX)

    private val isCompleted: Boolean
        get() = nonAppearanceInfo.contains(COMPLETED_REGEX) || isOneShot ||
            titleLabels.releaseSchedule == ReleaseSchedule.COMPLETED ||
            titleLabels.releaseSchedule == ReleaseSchedule.DISABLED

    private val isSimulpub: Boolean
        get() = isSimulReleased || titleLabels.isSimulpub

    private val isOnHiatus: Boolean
        get() = nonAppearanceInfo.contains(HIATUS_REGEX)

    private fun createGenres(intl: Intl): List<String> = buildList {
        if (isSimulpub && !isReEdition && !isOneShot && !isCompleted) {
            add("Simulrelease")
        }

        if (isOneShot) {
            add("One-shot")
        }

        if (isReEdition) {
            add("Re-edition")
        }

        if (isWebtoon) {
            add("Webtoon")
        }

        if (label?.magazine != null) {
            add(intl.format("serialization", label.magazine))
        }

        if (!isCompleted) {
            val scheduleLabel = intl["schedule_" + titleLabels.releaseSchedule.toString().lowercase()]
            add(intl.format("schedule", scheduleLabel))
        }

        val ratingLabel = intl["rating_" + rating.toString().lowercase()]
        add(intl.format("rating", ratingLabel))
    }

    private val viewingInformation: String?
        get() = viewingPeriodDescription.takeIf { !isCompleted }

    fun toSManga(intl: Intl): SManga = title.toSManga().apply {
        description = "${overview.orEmpty()}\n\n${viewingInformation.orEmpty()}".trim()
        status = when {
            isCompleted -> SManga.COMPLETED
            isOnHiatus -> SManga.ON_HIATUS
            else -> SManga.ONGOING
        }
        genre = createGenres(intl).joinToString()
    }

    companion object {
        private val COMPLETED_REGEX = "completado|complete|completo".toRegex()
        private val HIATUS_REGEX = "on a hiatus".toRegex(RegexOption.IGNORE_CASE)
        private val REEDITION_REGEX = "revival|remasterizada".toRegex()
    }
}

@Serializable
data class TitleLabels(
    val releaseSchedule: ReleaseSchedule = ReleaseSchedule.DISABLED,
    val isSimulpub: Boolean = false,
)

enum class ReleaseSchedule {
    DISABLED,
    EVERYDAY,
    WEEKLY,
    BIWEEKLY,
    MONTHLY,
    BIMONTHLY,
    TRIMONTHLY,
    OTHER,
    COMPLETED,
}

@Serializable
enum class Rating {
    @SerialName("ALLAGE")
    ALL_AGES,
    TEEN,

    @SerialName("TEENPLUS")
    TEEN_PLUS,
    MATURE,
}

@Serializable
data class Label(val label: LabelCode? = LabelCode.WEEKLY_SHOUNEN_JUMP) {
    val magazine: String?
        get() = when (label) {
            LabelCode.WEEKLY_SHOUNEN_JUMP -> "Weekly Shounen Jump"
            LabelCode.JUMP_SQUARE -> "Jump SQ."
            LabelCode.V_JUMP -> "V Jump"
            LabelCode.SHOUNEN_JUMP_GIGA -> "Shounen Jump GIGA"
            LabelCode.WEEKLY_YOUNG_JUMP -> "Weekly Young Jump"
            LabelCode.TONARI_NO_YOUNG_JUMP -> "Tonari no Young Jump"
            LabelCode.SHOUNEN_JUMP_PLUS -> "Shounen Jump+"
            LabelCode.MANGA_PLUS_CREATORS -> "MANGA Plus Creators"
            LabelCode.SAIKYOU_JUMP -> "Saikyou Jump"
            else -> null
        }
}

@Serializable
enum class LabelCode {
    @SerialName("CREATORS")
    MANGA_PLUS_CREATORS,

    @SerialName("GIGA")
    SHOUNEN_JUMP_GIGA,

    @SerialName("J_PLUS")
    SHOUNEN_JUMP_PLUS,

    OTHERS,

    REVIVAL,

    @SerialName("SKJ")
    SAIKYOU_JUMP,

    @SerialName("SQ")
    JUMP_SQUARE,

    @SerialName("TYJ")
    TONARI_NO_YOUNG_JUMP,

    @SerialName("VJ")
    V_JUMP,

    @SerialName("YJ")
    WEEKLY_YOUNG_JUMP,

    @SerialName("WSJ")
    WEEKLY_SHOUNEN_JUMP,
}

@Serializable
data class ChapterListGroup(
    val firstChapterList: List<Chapter> = emptyList(),
    val lastChapterList: List<Chapter> = emptyList(),
)

@Serializable
data class MangaViewer(
    val pages: List<MangaPlusPage> = emptyList(),
    val titleId: Int? = null,
    val titleName: String? = null,
)

@Serializable
data class Title(
    val titleId: Int,
    val name: String,
    val author: String? = null,
    val portraitImageUrl: String,
    val viewCount: Int = 0,
    val language: Language? = Language.ENGLISH,
) {

    fun toSManga(): SManga = SManga.create().apply {
        title = name
        author = this@Title.author?.replace(" / ", ", ")
        artist = author
        thumbnail_url = portraitImageUrl
        url = "#/titles/$titleId"
    }
}

enum class Language {
    ENGLISH,
    SPANISH,
    FRENCH,
    INDONESIAN,
    PORTUGUESE_BR,
    RUSSIAN,
    THAI,
    VIETNAMESE,
}

@Serializable
data class UpdatedTitleV2Group(
    val groupName: String,
    val titleGroups: List<OriginalTitleGroup> = emptyList(),
)

@Serializable
data class OriginalTitleGroup(
    val theTitle: String,
    val titles: List<UpdatedTitle> = emptyList(),
)

@Serializable
data class UpdatedTitle(val title: Title)

@Serializable
data class Chapter(
    val titleId: Int,
    val chapterId: Int,
    val name: String,
    val subTitle: String? = null,
    val startTimeStamp: Int,
    val endTimeStamp: Int,
    val isVerticalOnly: Boolean = false,
) {

    val isExpired: Boolean
        get() = subTitle == null

    fun toSChapter(): SChapter = SChapter.create().apply {
        name = "${this@Chapter.name} - $subTitle"
        date_upload = 1000L * startTimeStamp
        url = "#/viewer/$chapterId"
        chapter_number = this@Chapter.name.substringAfter("#").toFloatOrNull() ?: -1f
    }
}

@Serializable
data class MangaPlusPage(val mangaPage: MangaPage? = null)

@Serializable
data class MangaPage(
    val imageUrl: String,
    val width: Int,
    val height: Int,
    val encryptionKey: String? = null,
)
