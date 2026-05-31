package eu.kanade.tachiyomi.extension.all.luscious

const val POPULAR_DEFAULT_SORT_STATE = 0
const val LATEST_DEFAULT_SORT_STATE = 6
const val SEARCH_DEFAULT_SORT_STATE = 0

const val FILTER_VALUE_IGNORE = "<ignore>"

val ALBUM_LIST_REQUEST_GQL = $$"""
    query AlbumList($input: AlbumListInput!) {
        album {
            list(input: $input) {
                info {
                    page
                    has_next_page
                }
                items
            }
        }
    }
""".replace("\n", " ").replace("\\s+".toRegex(), " ")

val ALBUM_PICTURES_REQUEST_GQL = $$"""
    query AlbumListOwnPictures($input: PictureListInput!) {
        picture {
            list(input: $input) {
                info {
                    total_items
                    total_pages
                    page
                    has_next_page
                    items_per_page
                }
            items {
                created
                title
                url_to_original
                url_to_video
                position
                thumbnails {
                    url
                }
            }
        }
      }
    }
""".replace("\n", " ").replace("\\s+".toRegex(), " ")

val albumInfoQuery = $$"""
query AlbumGet($id: ID!) {
    album {
        get(id: $id) {
            ... on Album { ...AlbumStandard }
            ... on MutationError {
                errors {
                    code message
                 }
            }
        }
    }
}
fragment AlbumStandard on Album {
    __typename id title labels description created modified like_status number_of_favorites number_of_dislikes rating moderation_status marked_for_deletion marked_for_processing number_of_pictures number_of_animated_pictures number_of_duplicates slug is_manga url download_url permissions cover { width height size url } created_by { id url name display_name user_title avatar { url size } } content { id title url } language { id title url } tags { category text url count } genres { id title slug url } audiences { id title url url } last_viewed_picture { id position url } is_featured featured_date featured_by { id url name display_name user_title avatar { url size } }
}
""".trimIndent()

val albumListRelatedQuery = $$"""
query AlbumListRelated($id: ID!) {
    album {
        list_related(id: $id) {
            more_like_this { ...AlbumInSearchList }
            items_liked_like_this { ...AlbumInSearchList }
            items_created_by_this_user { ...AlbumInSearchList }
        }
    }
}
fragment AlbumInSearchList on Album {
    title url cover { url }
}
""".trimIndent()

const val MERGE_CHAPTER_PREF_KEY = "MERGE_CHAPTER"
const val MERGE_CHAPTER_PREF_TITLE = "Merge Chapter"
const val MERGE_CHAPTER_PREF_SUMMARY = "If checked, merges all content of one album into chapters of up to 1000 images each, labeled as 'Merged Chapter (Part 1)', 'Merged Chapter (Part 2)', and so on. Note: you must be logged into the WebView to access more than 1000 images."
const val MERGE_CHAPTER_PREF_DEFAULT_VALUE = false

const val RESOLUTION_PREF_KEY = "RESOLUTION"
const val RESOLUTION_PREF_TITLE = "Image resolution"
val RESOLUTION_PREF_ENTRIES = arrayOf("Low", "Medium", "High", "Original")
val RESOLUTION_PREF_ENTRY_VALUES = arrayOf("2", "1", "0", "-1")
val RESOLUTION_PREF_DEFAULT_VALUE = RESOLUTION_PREF_ENTRY_VALUES[3]

const val SORT_PREF_KEY = "SORT"
const val SORT_PREF_TITLE = "Page Sort"
val SORT_PREF_ENTRIES = arrayOf("Position", "Date", "Rating")
val SORT_PREF_ENTRY_VALUES = arrayOf("position", "date_newest", "rating_all_time")
val SORT_PREF_DEFAULT_VALUE = SORT_PREF_ENTRY_VALUES[0]

const val MIRROR_PREF_KEY = "MIRROR"
const val MIRROR_PREF_TITLE = "Mirror"
val MIRROR_PREF_ENTRIES = arrayOf("Guest", "Members")
val MIRROR_PREF_ENTRY_VALUES = arrayOf("https://www.luscious.net", "https://members.luscious.net")
val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]
