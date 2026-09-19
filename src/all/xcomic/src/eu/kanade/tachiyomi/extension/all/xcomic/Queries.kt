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
    val origStatus: String? = null,
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

// ── Server laws (probe-verified; see field ledger in DTOs) ─────────
//  · ONE root field per operation — aliases count as roots. Rejections
//    ship as HTTP 200 + errors array; branch on errors, never status.
//  · Browse pager fabricates counts → never request get_title_browse_pager;
//    hasNextPage = page fullness. Chapter-list totals ARE true → trusted.
//  · Gate model: scalars + tracking_sites + comic_ids live on Title;
//    sourceNodes/tagNodes/owner_titleNode/tracking_scores/vote_scores
//    and ALL upload-state fields are rejected there. On ComicData,
//    profileNodes/groupNodes/srcName are gated; subName/dbStatus/
//    isPublic/chapterNode_up_to are LIVE. readDirection/summary/
//    extraInfo exist ONLY on ComicData — probed and rejected on titles.

/**
 * PHASE 1 — browse / search / popular / latest. One row per work.
 * Minimal by design: identity + covers + the comic_ids bridge.
 * `translated_languages` may contain null elements (DTO uses List<String?>?).
 */
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
            }
        }
    }
"""

/**
 * PHASE 2 — full title node: details backbone + source resolution.
 * Root is `get_title_titleNode` (doubled prefix; plain `get_titleNode` 400s).
 * Complete verified surface (28 live fields; field-discovery receipted).
 * Totals are display-only (they lie); `chap_last_public_at` is the honest
 * work-level freshness signal. `comic_ids` bridges to source resolution.
 * Deliberately ABSENT (rejected on this type): readDirection, summary,
 * extraInfo, sourceNodes, tagNodes, tracking_scores, vote_scores.
 */
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

/**
 * PHASE 3 — source node, dual duty: language probe against comic_ids
 * AND full details of the CHOSEN source. Also the legacy path (old
 * library entries, /source/ + /comic/ deep links, id: pasted ids).
 *
 * The ONLY supplier of readDirection/summary/extraInfo (rejected at
 * title level). summary/extraInfo are RichText OBJECTS — do not flatten.
 * dbStatus/isPublic feed isLive() dead-source filtering; subName is the
 * upload label. trackingSites (5) kept optional — title's 7-field set
 * supersedes it for display; drop this subselection if undesired.
 */
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

/**
 * Chapter versions for one source — totals TRUE (trusted for pagination).
 * `volIdx` REMOVED: no longer a ChapterData field; requesting it 400s.
 * `sfw_result`/`chaDuplications` REMOVED: never read; tolerant DTO no
 * longer needed when the fields aren't requested at all.
 */
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

/** Deduplicated view — same fixes; accepts chapter_desc/asc only. */
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

/** Reader pages — unchanged, live-verified. */
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

/**
 * Minimal source probe — fired fan-out per comic_ids entry at browse time
 * (server law: one root per operation, no batching/aliases). Browse-row
 * fields only; all live-verified.
 */
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
