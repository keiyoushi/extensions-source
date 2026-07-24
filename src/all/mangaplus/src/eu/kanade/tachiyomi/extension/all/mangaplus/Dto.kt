package eu.kanade.tachiyomi.extension.all.mangaplus

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class MangaPlusResponse(
    @ProtoNumber(1) val success: SuccessResult? = null,
    @ProtoNumber(2) val error: ErrorResult? = null,
)

@Serializable
class ErrorResult(
    @ProtoNumber(2) val englishPopup: Popup? = null,
    @ProtoNumber(3) val spanishPopup: Popup? = null,
) {
    fun langPopup(langCode: Int): Popup? = when (langCode) {
        LANGUAGE_SPANISH -> spanishPopup ?: englishPopup
        else -> englishPopup
    }
}

@Serializable
class Popup(
    @ProtoNumber(1) val subject: String = "",
    @ProtoNumber(2) val body: String = "",
)

@Serializable
class SuccessResult(
    @ProtoNumber(8) val titleDetailView: TitleDetailView? = null,
    @ProtoNumber(10) val mangaViewer: MangaViewer? = null,
    @ProtoNumber(25) val allTitlesView: AllTitlesView? = null,
    @ProtoNumber(35) val allTitlesViewV3: AllTitlesViewV3? = null,
    @ProtoNumber(37) val titleRankingView: TitleRankingView? = null,
    @ProtoNumber(38) val webHomeView: WebHomeView? = null,
)

@Serializable
class AllTitlesViewV3(
    @ProtoNumber(2) val tags: List<TagName> = emptyList(),
    @ProtoNumber(3) val titles: List<AllTitlesV3Entry> = emptyList(),
)

@Serializable
class AllTitlesV3Entry(
    @ProtoNumber(2) val title: Title,
    @ProtoNumber(3) val genres: List<TagName> = emptyList(),
)

@Serializable
class TitleRankingView(
    @ProtoNumber(3) val rankedTitles: List<RankedTitle> = emptyList(),
)

@Serializable
class RankedTitle(
    @ProtoNumber(2) val titles: List<Title> = emptyList(),
)

@Serializable
class AllTitlesView(
    @ProtoNumber(1) val allTitlesGroup: List<AllTitlesGroup> = emptyList(),
)

@Serializable
class AllTitlesGroup(
    @ProtoNumber(2) val titles: List<Title> = emptyList(),
)

@Serializable
class WebHomeView(
    @ProtoNumber(2) val groups: List<UpdatedTitleGroup> = emptyList(),
)

@Serializable
class UpdatedTitleGroup(
    @ProtoNumber(2) val titles: List<UpdatedTitle> = emptyList(),
)

@Serializable
class UpdatedTitle(
    @ProtoNumber(3) private val latestChapter: LatestChapter? = null,
) {
    val title: Title? get() = latestChapter?.title
}

@Serializable
class LatestChapter(
    @ProtoNumber(1) val title: Title,
)

@Serializable
class Title(
    @ProtoNumber(1) val titleId: Int,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val author: String? = null,
    @ProtoNumber(4) val portraitImageUrl: String = "",
    @ProtoNumber(7) val language: Int = LANGUAGE_ENGLISH,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = name
        author = this@Title.author?.replace(" / ", ", ")
        artist = author
        thumbnail_url = portraitImageUrl
        url = "#/titles/$titleId"
    }
}

@Serializable
class TitleDetailView(
    @ProtoNumber(1) val title: Title,
    @ProtoNumber(3) val overview: String = "",
    @ProtoNumber(7) val viewingPeriodDescription: String = "",
    @ProtoNumber(8) val nonAppearanceInfo: String = "",
    @ProtoNumber(28) val chapterListGroup: List<ChapterListGroup> = emptyList(),
    @ProtoNumber(31) val genreList: List<TagName> = emptyList(),
) {
    val chapterList: List<Chapter>
        get() = chapterListGroup.flatMap { it.firstChapterList + it.lastChapterList }

    private val isOneShot: Boolean
        get() = genreList.any { it.slug == "one-shot" }

    fun toSManga(): SManga = title.toSManga().apply {
        description = listOf(overview, viewingPeriodDescription)
            .filter(String::isNotEmpty)
            .joinToString("\n\n")
        genre = genreList.mapNotNull { it.name.takeIf(String::isNotEmpty) }.joinToString()
        status = when {
            isOneShot || nonAppearanceInfo.contains(COMPLETED_REGEX) -> SManga.COMPLETED
            nonAppearanceInfo.contains(HIATUS_REGEX) -> SManga.ON_HIATUS
            else -> SManga.ONGOING
        }
    }

    companion object {
        private val COMPLETED_REGEX = "completado|complete|completo".toRegex(RegexOption.IGNORE_CASE)
        private val HIATUS_REGEX = "on a hiatus".toRegex(RegexOption.IGNORE_CASE)
    }
}

@Serializable
class TagName(
    @ProtoNumber(1) val name: String = "",
    @ProtoNumber(2) val slug: String = "",
)

@Serializable
class ChapterListGroup(
    @ProtoNumber(2) val firstChapterList: List<Chapter> = emptyList(),
    @ProtoNumber(4) val lastChapterList: List<Chapter> = emptyList(),
)

@Serializable
class Chapter(
    @ProtoNumber(2) val chapterId: Int,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val subTitle: String? = null,
    @ProtoNumber(6) val startTimeStamp: Int = 0,
) {
    val isExpired: Boolean
        get() = subTitle == null

    fun toSChapter(subtitlePref: Boolean): SChapter = SChapter.create().apply {
        name = if (subtitlePref && subTitle != null) {
            subTitle
        } else {
            "${this@Chapter.name} - $subTitle"
        }
        date_upload = 1000L * startTimeStamp
        url = "#/viewer/$chapterId"
        chapter_number = this@Chapter.name.substringAfter("#").toFloatOrNull() ?: -1f
        scanlator = "MANGA Plus"
    }
}

@Serializable
class MangaViewer(
    @ProtoNumber(1) val pages: List<MangaPlusPage> = emptyList(),
    @ProtoNumber(9) val titleId: Int? = null,
    @ProtoNumber(19) val viewToken: String? = null,
)

@Serializable
class MangaPlusPage(
    @ProtoNumber(1) val mangaPage: MangaPage? = null,
)

@Serializable
class MangaPage(
    @ProtoNumber(1) val imageUrl: String,
    @ProtoNumber(5) val encryptionKey: String? = null,
)

const val LANGUAGE_ENGLISH = 0
const val LANGUAGE_SPANISH = 1
const val LANGUAGE_FRENCH = 2
const val LANGUAGE_INDONESIAN = 3
const val LANGUAGE_PORTUGUESE_BR = 4
const val LANGUAGE_RUSSIAN = 5
const val LANGUAGE_THAI = 6
const val LANGUAGE_GERMAN = 7
const val LANGUAGE_VIETNAMESE = 9
