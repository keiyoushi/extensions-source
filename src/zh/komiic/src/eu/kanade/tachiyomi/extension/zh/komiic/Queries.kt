package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.MangasPage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

private fun buildQuery(query: String): String = query.trimIndent()
    .replace("#{body}", COMIC_BODY.trimIndent())
    .replace("%", "$")

private const val COMIC_BODY =
    """
    {
      id
      title
      description
      status
      imageUrl
      authors {
        id
        name
      }
      categories {
        id
        name
      }
    }
    """

private fun buildRequestBody(query: String, variables: JsonObject): RequestBody {
    val body = buildJsonObject {
        put("query", query)
        put("variables", variables)
    }
    val contentType = "application/json".toMediaType()
    return Json.encodeToString(body).toByteArray().toRequestBody(contentType)
}

fun parseListing(data: DataDto): MangasPage {
    data.allCategory?.let { categories = it }
    val listing = data.getListing()
    val entries = listing.map { it.toSManga() }
    val hasNextPage = listing.size == PAGE_SIZE
    return MangasPage(entries, hasNextPage)
}

fun listingQuery(variables: ListingVariables): RequestBody {
    if (variables.pagination.orderBy == OrderBy.MONTH_VIEWS) return popularQuery(variables)
    val query = buildQuery(
        """
        query comicByCategories(%categoryId: [ID!]!, %pagination: Pagination!) {
          comics: comicByCategories(categoryId: %categoryId, pagination: %pagination) #{body}
          allCategory { id name }
        }
        """,
    )
    return buildRequestBody(query, variables.encode())
}

private fun popularQuery(variables: ListingVariables): RequestBody {
    if (variables.categoryId.isNotEmpty()) throw Exception("“本月最夯”不能篩選類型")
    val query = buildQuery(
        """
        query hotComics(%pagination: Pagination!) {
          comics: hotComics(pagination: %pagination) #{body}
          allCategory { id name }
        }
        """,
    )
    return buildRequestBody(query, variables.encode())
}

fun searchQuery(keyword: String): RequestBody {
    val query = buildQuery(
        """
        query searchComicAndAuthorQuery(%keyword: String!) {
          searchComicsAndAuthors(keyword: %keyword) {
            comics #{body}
          }
          allCategory { id name }
        }
        """,
    )
    val variables = buildJsonObject {
        put("keyword", keyword)
    }
    return buildRequestBody(query, variables)
}

fun idsQuery(id: String): RequestBody {
    val query = buildQuery(
        """
        query comicByIds(%comicIds: [ID]!) {
          comics: comicByIds(comicIds: %comicIds) #{body}
        }
        """,
    )
    val variables = buildJsonObject {
        putJsonArray("comicIds") {
            add(id)
        }
    }
    return buildRequestBody(query, variables)
}

fun mangaQuery(id: String): RequestBody {
    val query = buildQuery(
        """
        query chapterByComicId(%comicId: ID!) {
          comicById(comicId: %comicId) #{body}
          chaptersByComicId(comicId: %comicId) {
            id
            serial
            type
            size
            dateCreated
          }
        }
        """,
    )
    val variables = buildJsonObject {
        put("comicId", id)
    }
    return buildRequestBody(query, variables)
}

fun pageListQuery(chapterId: String): RequestBody {
    val query = buildQuery(
        """
        query imagesByChapterId(%chapterId: ID!) {
          reachedImageLimit
          imagesByChapterId(chapterId: %chapterId) {
            kid
          }
        }
        """,
    )
    val variables = buildJsonObject {
        put("chapterId", chapterId)
    }
    return buildRequestBody(query, variables)
}
