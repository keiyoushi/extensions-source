package eu.kanade.tachiyomi.extension.all.luscious

const val POPULAR_DEFAULT_SORT_STATE = 0
const val LATEST_DEFAULT_SORT_STATE = 6

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
""".replace("\n", " ").replace("""\s+""".toRegex(), " ")

val ALBUM_PICTURES_REQUEST_GQL = $$"""
    query AlbumListOwnPictures($input: PictureListInput!) {
        picture {
            list(input: $input) {
                info {
                    total_pages
                }
            items {
                url_to_original
                url_to_video
                thumbnails {
                    url
                    width
                    height
                    size
                }
            }
        }
      }
    }
""".replace("\n", " ").replace("""\s+""".toRegex(), " ")

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

const val PICTURES_PER_PAGE = 50
const val PAGES_PER_CHUNK = 20

const val RESOLUTION_PREF_KEY = "RESOLUTION"
const val RESOLUTION_PREF_TITLE = "Image resolution"
val RESOLUTION_PREF_ENTRIES = arrayOf("Low", "Medium", "High", "Original")
val RESOLUTION_PREF_ENTRY_VALUES = arrayOf("2", "1", "0", "-1")
val RESOLUTION_PREF_DEFAULT_VALUE = RESOLUTION_PREF_ENTRY_VALUES[3]
val RESOLUTION_SIZES = mapOf("2" to "small", "1" to "medium", "0" to "xMax")

const val SORT_PREF_KEY = "SORT"
const val SORT_PREF_TITLE = "Page Sort"
val SORT_PREF_ENTRIES = arrayOf("Position", "Date", "Rating")
val SORT_PREF_ENTRY_VALUES = arrayOf("date_oldest", "date_newest", "rating_all_time")
val SORT_PREF_DEFAULT_VALUE = SORT_PREF_ENTRY_VALUES[0]
