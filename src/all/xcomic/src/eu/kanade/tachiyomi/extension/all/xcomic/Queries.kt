package eu.kanade.tachiyomi.extension.all.xcomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val buildQueryRegex = Regex("\\s+")
private fun buildQuery(queryAction: () -> String): String = queryAction()
    .replace("%", "$")
    .replace(buildQueryRegex, " ")

// =========================== Payload Dto ============================

@Serializable
class ApiComicNodeVariables(val id: String)

@Serializable
class ApiComicSearchVariables(
    val word: String? = null,
    val page: Int? = null,
    val size: Int? = null,
    val init: Int? = null,
    val sortby: String? = null,
    val where: String? = null,
    val releaseYearMin: Int? = null,
    val releaseYearMax: Int? = null,
    val incTypes: List<String>? = null,
    val incDemographics: List<String>? = null,
    val incContentRatings: List<String>? = null,
    val incOLangs: List<String>? = null,
    val incTLangs: List<String>? = null,
    val incGenres: List<String>? = null,
    val excGenres: List<String>? = null,
    val incGenresMode: String? = null,
    val excGenresMode: String? = null,
    val origStatus: String? = null,
    val siteStatus: String? = null,
    val chapCount: String? = null,
    val ignoreGlobalGenres: Boolean? = null,
)

@Serializable
class ApiChapterListSelect(
    @SerialName("comic_id")
    val comicId: String,
    val page: Int? = null,
    val size: Int? = null,
)

// ============================ Wrappers ==============================

@Serializable
class ApiComicSearchWrapper(val select: ApiComicSearchVariables)

@Serializable
class ApiChapterListWrapper(val select: ApiChapterListSelect)

// ============================= Queries ==============================

private const val COMIC_FIELDS = """
    data {
        id
        name
        altNames
        authors
        artists
        originalLanguage
        translatedLanguage
        originalStatus
        originalPubFrom { y m d }
        originalPubTill { y m d }
        originalPubZone
        uploadStatus
        type
        demographics
        contentRating
        genres
        tags
        tagNodes {
            data {
                name
            }
        }
        publishers
        publisherNodes {
            data {
                name
            }
        }
        is_hot
        is_new
        follows
        reviews
        comments_total
        score_val
        chaps_normal
        trackingSites {
            mangaupdates
            myanimelist
            animeplanet
            anilist
            kitsu
        }
        summary
        extraInfo
        urlPath
        urlCover
    }
"""

val COMIC_NODE_QUERY = buildQuery {
    """
    query(%id: ID!) {
        get_comicNode(id: %id) {
            $COMIC_FIELDS
        }
    }
    """
}

val COMIC_SEARCH_QUERY = buildQuery {
    """
    query(%select: Comic_Browse_Select) {
        get_comic_browse_pager(select: %select) {
            next
        }
        get_comic_browse_items(select: %select) {
            $COMIC_FIELDS
        }
    }
    """
}

val CHAPTER_LIST_QUERY = buildQuery {
    """
    query(%select: Select_Comic_ChapterList) {
        get_comic_chapterList(select: %select) {
            paging {
                next
            }
            items {
                id
                data {
                    id
                    serial
                    chaNum
                    volNum
                    dname
                    title
                    urlPath
                    dateCreate
                    dateModify
                    datePublic
                    userNode {
                        data {
                            name
                        }
                    }
                    groupNodes {
                        data {
                            name
                        }
                    }
                }
            }
        }
    }
    """
}
