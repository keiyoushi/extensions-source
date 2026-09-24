package eu.kanade.tachiyomi.extension.all.xcomic

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ============================= Variables =============================
/** Title browse — search / popular / latest. */
@Serializable
class ApiTitleBrowseVariables(
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
class ApiTitleBrowseWrapper(val select: ApiTitleBrowseVariables)

/** Title node — details backbone + comic_ids source resolution. */
@Serializable
class ApiTitleNodeVariables(val id: String)

/** Source node — probe + chosen-source details + legacy path. */
@Serializable
class ApiComicNodeVariables(val id: String)

/** Reader — page image URLs. */
@Serializable
class ApiChapterNodeVariables(val id: String)

/** Chapter list for one source. */
@Serializable
class ApiChapterListSelect(
    @SerialName("comic_id")
    val comicId: String,
    val page: Int? = null,
    val size: Int? = null,
    val sortby: String = "chapter_desc",
)

@Serializable
class ApiChapterListWrapper(val select: ApiChapterListSelect)

// ============================= Queries ==============================
val TITLE_BROWSE_QUERY = $$"""
    query get_title_browse($select: Title_Browse_Select) {
        get_title_browse_items(select: $select) {
            id
            data {
                title
                native_title
                romanized_title
                original_language
                translated_languages
                type
                cover_local_url
                cover_url
                comic_ids
                chap_last_public_at
            }
        }
    }
"""

val TITLE_NODE_QUERY = $$"""
    query get_title_titleNode($id: ID!) {
        get_title_titleNode(id: $id) {
            id
            data {
                title
                alt_titles
                native_title
                romanized_title
                original_language
                translated_languages
                authors
                artists
                year
                type
                status
                description
                cover_local_url
                cover_local
                cover_url
                urlPath
                total_comics
                total_chapters
                total_follows
                total_reviews
                total_comments
                vote_avg
                vote_users
                vote_bay
                vote_val
                chap_last_public_at
                created_at
                updated_at
                is_merged
                merged_to
                comic_ids
                content_rating_id
                type_id
                demographic_ids
                genre_ids
                format_ids
                tracking_sites {
                    anilist
                    myanimelist
                    mangaupdates
                    kitsu
                    animeplanet
                    shikimori
                    mangabaka
                }
            }
        }
    }
"""

val COMIC_NODE_QUERY = $$"""
    query get_comicNode($id: ID!) {
        get_comicNode(id: $id) {
            id
            data {
                id
                name
                subName
                altNames
                authors
                artists
                originalLanguage
                translatedLanguage
                originalStatus
                uploadStatus
                type
                demographics
                contentRating
                genres
                tags
                publishers
                dbStatus
                isPublic
                follows
                reviews
                comments_total
                score_val
                is_hot
                is_new
                originalPubFrom { y m d }
                originalPubTill { y m d }
                originalPubZone
                chaps_normal
                dateUpload
                chapterNode_up_to {
                    id
                    data {
                        dname
                        datePublic
                    }
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
                    dateCreate
                    datePublic
                    dateModify
                    chaNum
                    volNum
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
                    dateCreate
                    datePublic
                    dateModify
                    chaNum
                    volNum
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

val COMIC_PROBE_QUERY = $$"""
    query get_comicNode($id: ID!) {
        get_comicNode(id: $id) {
            id
            data {
                name
                subName
                dbStatus
                isPublic
                translatedLanguage
                chaps_normal
                urlPath
                urlCover
            }
        }
    }
"""
