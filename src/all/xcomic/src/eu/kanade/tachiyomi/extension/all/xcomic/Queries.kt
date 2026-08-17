package eu.kanade.tachiyomi.extension.all.xcomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiComicNodeVariables(val id: String)

@Serializable
class ApiChapterNodeVariables(val id: String)

@Serializable
class ApiComicSearchVariables(
    val word: String = "",
    val page: Int? = null,
    val size: Int? = null,
    val init: Int? = null,
    val sortby: String? = null,
    val where: String? = null,
    val releaseYearMin: Int? = null,
    val releaseYearMax: Int? = null,
    val incTypes: List<String> = emptyList(),
    val incDemographics: List<String> = emptyList(),
    val incContentRatings: List<String> = emptyList(),
    val incOLangs: List<String> = emptyList(),
    val incTLangs: List<String> = emptyList(),
    val incGenres: List<String> = emptyList(),
    val excGenres: List<String> = emptyList(),
    val incGenresMode: String? = null,
    val excGenresMode: String? = null,
    val origStatus: String? = null,
    val siteStatus: String? = null,
    val chapCount: String? = null,
    val ignoreGlobalGenres: Boolean = false,
    val ignoreGlobalULangs: Boolean = false,
    val ignoreGlobalBlocks: Boolean = false,
)

@Serializable
class ApiChapterListSelect(
    @SerialName("comic_id")
    val comicId: String,
    val page: Int? = null,
    val size: Int? = null,
    val sortby: String = "chapter_desc",
)

@Serializable
class ApiComicSearchWrapper(val select: ApiComicSearchVariables)

@Serializable
class ApiChapterListWrapper(val select: ApiChapterListSelect)

// ============================= Queries ==============================

val COMIC_NODE_QUERY = $$"""
    query get_comicNode($id: ID!) {
        get_comicNode(id: $id) {
            data {
                id
                name
                altNames
                authors
                authorNodes {
                    id
                    data {
                        id
                        name
                    }
                }
                artists
                artistNodes {
                    id
                    data {
                        id
                        name
                    }
                }
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
                    id
                    data {
                        id
                        name
                    }
                }
                publishers
                publisherNodes {
                    id
                    data {
                        id
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
                summary {
                    text
                }
                extraInfo {
                    text
                }
                readDirection
                urlPath
                urlCover
            }
        }
    }
"""

val COMIC_ITEMS_QUERY = $$"""
    query get_comic_browse_items($select: Comic_Browse_Select) {
        get_comic_browse_items(select: $select) {
            data {
                id
                name
                urlPath
                urlCover
            }
        }
    }
"""

val CHAPTER_LIST_QUERY = $$"""
    query get_comic_chapterList_fullList($select: Select_Comic_ChapterList) {
        get_comic_chapterList_fullList(select: $select) {
            paging {
                next
                total
            }
            items {
                id
                data {
                    id
                    comicId
                    dbStatus
                    isFinal
                    volume
                    serial
                    dname
                    title
                    urlPath
                    sfw_result
                    chaDuplications
                    dateCreate
                    datePublic
                    dateModify
                    chaNum
                    volNum
                    volIdx
                    count_images
                    is_new
                    srcName
                    srcTitle
                    srcColor
                    comments_topic
                    comments_total
                    views_login
                    views_guest
                    profileNodes {
                        data {
                            name
                        }
                    }
                }
            }
        }
    }
"""

val CHAPTER_UNIQ_LIST_QUERY = $$"""
    query get_comic_chapterList_uniqList($select: Select_Comic_ChapterList_UniqList) {
        get_comic_chapterList_uniqList(select: $select) {
            paging {
                next
                total
            }
            items {
                id
                data {
                    id
                    comicId
                    dbStatus
                    isFinal
                    volume
                    serial
                    dname
                    title
                    urlPath
                    sfw_result
                    chaDuplications
                    dateCreate
                    datePublic
                    dateModify
                    chaNum
                    volNum
                    volIdx
                    count_images
                    is_new
                    srcName
                    srcTitle
                    srcColor
                    comments_topic
                    comments_total
                    views_login
                    views_guest
                    profileNodes {
                        data {
                            name
                        }
                    }
                }
            }
        }
    }
"""

val CHAPTER_PAGES_QUERY = $$"""
    query($id: ID!) {
        get_chapterNode(id: $id) {
            id
            data {
                imageUrls
            }
        }
    }
"""

val COMIC_LATEST_QUERY = $$"""
    query get_comic_latestUploads($select: Comic_LatestUploads_Select) {
        get_comic_latestUploads(select: $select) {
            before
            items {
                comic {
                    id
                    data {
                        id
                        name
                        urlPath
                        urlCover
                        translatedLanguage
                        genres
                    }
                }
                chapters(amount: 1) {
                    id
                    data {
                        id
                        datePublic
                    }
                }
            }
        }
    }
"""
