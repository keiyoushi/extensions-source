package eu.kanade.tachiyomi.extension.all.xcomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ApiComicNodeVariables(val id: String)

@Serializable
class ApiChapterNodeVariables(val id: String)

@Serializable
class ApiTitleSearchVariables(
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
    val origStatus: List<String> = emptyList(),
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
class ApiTitleSearchWrapper(val select: ApiTitleSearchVariables)

@Serializable
class ApiChapterListWrapper(val select: ApiChapterListSelect)

// ============================= Queries ==============================

// The site split works into titles (the work) and comics (per-language editions).
// A comic node carries its title's full data, so it doubles as the details endpoint.
val COMIC_NODE_QUERY = $$"""
    query get_comicNode($id: ID!) {
        get_comicNode(id: $id) {
            id
            data {
                id
                name
                slug
                translatedLanguage
                readDirection
                originalPubFrom { y m d }
                originalPubTill { y m d }
                originalPubZone
                chaps_normal
                uploadStatus
                summary { code html text }
                extraInfo { code html text }
                authorNodes {
                    id
                    data {
                        id
                        name
                    }
                }
                artistNodes {
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
                title_titleNode {
                    id
                    data {
                        id
                        title
                        alt_titles
                        authors
                        artists
                        year
                        status
                        description
                        original_language
                        content_rating_id
                        type_id
                        demographic_ids
                        genre_ids
                        format_ids
                        cover_url
                        cover_local_url
                        urlPath
                        total_chapters
                        total_follows
                        total_reviews
                        total_comments
                        vote_val
                        tracking_sites {
                            mangaupdates
                            myanimelist
                            animeplanet
                            anilist
                            kitsu
                            mangabaka
                            shikimori
                        }
                    }
                }
            }
        }
    }
"""

val TITLE_ITEMS_QUERY = $$"""
    query get_title_browse_items($select: Title_Browse_Select) {
        get_title_browse_items(select: $select) {
            id
            data {
                id
                title
                status
                cover_url
                cover_local_url
                urlPath
            }
        }
    }
"""

val TITLE_PAGER_QUERY = $$"""
    query get_title_browse_pager($select: Title_Browse_Select) {
        get_title_browse_pager(select: $select) {
            next
            total
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
                        id
                        data {
                            id
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
