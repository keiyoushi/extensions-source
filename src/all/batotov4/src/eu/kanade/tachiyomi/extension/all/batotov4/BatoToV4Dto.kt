package eu.kanade.tachiyomi.extension.all.batotov4

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GraphQLPayload<T>(
    val variables: T,
    val query: String,
)

// ************ Comic Search ************ //
@Serializable
data class ApiComicSearchVariables(
    val select: Select,
) {
    @Serializable
    data class Select(
        val page: Int,
        val size: Int,
        val where: String,
        val word: String,
        val sortby: String,
        val incGenres: List<String>,
        val excGenres: List<String>,
        val incOLangs: List<String>,
        val incTLangs: List<String>,
        val origStatus: String,
        val siteStatus: String,
        val chapCount: String,
    )

    constructor(
        pageNumber: Int,
        size: Int,
        sortby: String?,
        query: String = "",
        where: String = "browse",
        incGenres: List<String>? = emptyList(),
        excGenres: List<String>? = emptyList(),
        incOLangs: List<String>? = emptyList(),
        incTLangs: List<String>? = emptyList(),
        origStatus: String? = "",
        siteStatus: String? = "",
        chapCount: String? = "",
    ) : this(
        Select(
            page = pageNumber,
            size = size,
            where = where,
            word = query,
            sortby = sortby ?: "",
            incGenres = incGenres ?: emptyList(),
            excGenres = excGenres ?: emptyList(),
            incOLangs = incOLangs ?: emptyList(),
            incTLangs = incTLangs ?: emptyList(),
            origStatus = origStatus ?: "",
            siteStatus = siteStatus ?: "",
            chapCount = chapCount ?: "",
        ),
    )
}

@Serializable
data class ApiComicSearchResponse(
    val data: SearchData,
) {
    @Serializable
    data class SearchData(
        @SerialName("get_comic_browse") val response: Comic_Browse_Result,
    ) {
        @Serializable
        data class Comic_Browse_Result(
            val paging: Paging,
            val items: List<ComicNode>,
        ) {
            @Serializable
            data class Paging(
                val pages: Int,
                val page: Int,
                val next: Int,
            )

            @Serializable
            data class ComicNode(
                val data: ComicData,
            ) {
                @Serializable
                data class ComicData(
                    val id: String,
                    val name: String,
                    val urlPath: String,
                    val urlCover300: String? = null,
                    val urlCover600: String? = null,
                    val urlCover900: String? = null,
                    val urlCoverOri: String? = null,
                ) {
                    fun toSManga(baseUrl: String): SManga = SManga.create().apply {
                        url = urlPath
                        title = name
                        thumbnail_url = "$baseUrl${urlCoverOri ?: urlCover600 ?: urlCover900 ?: urlCover300}"
                    }
                }
            }
        }
    }
}

// ************ Manga Details ************ //
@Serializable
data class ApiComicNodeVariables(
    val id: String,
)

@Serializable
data class ApiComicNodeResponse(
    val data: ComicNodeData,
) {
    @Serializable
    data class ComicNodeData(
        @SerialName("get_comicNode") val response: ComicNode,
    ) {
        @Serializable
        data class ComicNode(
            val data: ComicData,
        ) {
            @Serializable
            data class ComicData(
                val id: String,
                val name: String,
                val altNames: List<String>? = null,
                val authors: List<String>? = null,
                val artists: List<String>? = null,
                val originalStatus: String? = null,
                val uploadStatus: String? = null,
                val genres: List<String>? = null,
                val summary: String? = null,
                val extraInfo: String? = null,
                val urlPath: String,
                val urlCover300: String? = null,
                val urlCover600: String? = null,
                val urlCover900: String? = null,
                val urlCoverOri: String? = null,
            ) {
                fun toSManga(baseUrl: String): SManga = SManga.create().apply {
                    url = urlPath
                    title = name
                    author = authors?.joinToString()
                    artist = artists?.joinToString()
                    genre = genres?.joinToString { genre -> // Map to the canonical name
                        GenreGroupFilter.options.find { it.value == genre }?.name ?: genre
                    }
                    status = 0 // Status needs to be parsed separately
                    thumbnail_url = "$baseUrl${urlCoverOri ?: urlCover600 ?: urlCover900 ?: urlCover300}"
                    description = buildString {
                        if (!summary.isNullOrEmpty()) {
                            append(summary)
                        }
                        if (!extraInfo.isNullOrEmpty()) {
                            if (isNotEmpty()) append("\n\nExtra Info:\n")
                            append(extraInfo)
                        }
                        if (!altNames.isNullOrEmpty()) {
                            if (isNotEmpty()) append("\n\n")
                            append("Alternative Titles:\n")
                            append(altNames.joinToString("\n") { "• $it" })
                        }
                    }
                }
            }
        }
    }
}

// ************ Chapter List ************ //
@Serializable
data class ApiChapterListVariables(
    val comicId: String,
    val start: Int, // set to -1 to grab all chapters
)

@Serializable
data class ApiChapterListResponse(
    val data: ChapterListData,
) {
    @Serializable
    data class ChapterListData(
        @SerialName("get_comic_chapterList") val response: List<ChapterNode>,
    ) {
        @Serializable
        data class ChapterNode(
            val data: ChapterData,
        ) {
            @Serializable
            data class ChapterData(
                val id: String,
                val dname: String? = null,
                val title: String? = null,
                val urlPath: String,
                val dateCreate: Long? = null,
                val dateModify: Long? = null,
            ) {
                fun toSChapter(): SChapter = SChapter.create().apply {
                    url = urlPath
                    name = buildString {
                        if (!dname.isNullOrEmpty()) {
                            append(dname)
                        }
                        if (!title.isNullOrEmpty()) {
                            if (isNotEmpty()) append(": ")
                            append(title)
                        }
                    }.ifEmpty { "Unnamed Chapter: $id" }
                    date_upload = dateModify ?: dateCreate ?: 0L
                }
            }
        }
    }
}

// ************ Chapter Pages ************ //
@Serializable
data class ApiChapterNodeVariables(
    val id: String,
)

@Serializable
data class ApiChapterNodeResponse(
    val data: ChapterNodeData,
) {
    @Serializable
    data class ChapterNodeData(
        @SerialName("get_chapterNode") val response: ChapterNode,
    ) {
        @Serializable
        data class ChapterNode(
            val data: ChapterData,
        ) {
            @Serializable
            data class ChapterData(
                val imageFile: ChapterImageFile,
            ) {
                @Serializable
                data class ChapterImageFile(
                    val urlList: List<String>,
                )
            }
        }
    }
}
