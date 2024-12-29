package eu.kanade.tachiyomi.extension.en.ninehentai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Manga(
    val id: Int,
    val title: String,
    val image_server: String,
    val total_page: Int,
)

/*
The basic search request JSON object looks like this:
{
  "search": {
    "text": "",
    "page": 1,
    "sort": 1,
    "pages": {
      "range": [0, 2000]
    },
    "tag": {
      "items": {
        "included": [],
        "excluded": []
      }
    }
  }
}
*/

/*
 Sort = 0, Newest
 Sort = 1, Popular right now
 Sort = 2, Most Fapped
 Sort = 3, Most Viewed
 Sort = 4, By title
 */

@Serializable
data class SearchRequest(
    val text: String,
    val page: Int,
    val sort: Int,
    val pages: Range,
    val tag: Items,
)

@Serializable
data class SearchRequestPayload(
    val search: SearchRequest,
)

@Serializable
data class SearchResponse(
    @SerialName("total_count") val totalCount: Int,
    val results: List<Manga>,
)

@Serializable
data class Range(
    val range: List<Int>,
)

@Serializable
data class Items(
    val items: TagArrays,
)

@Serializable
data class TagArrays(
    val included: List<Tag>,
    val excluded: List<Tag>,
)

@Serializable
data class Tag(
    val id: Int,
    val name: String,
    val description: String? = null,
    val type: Int = 1,
)
