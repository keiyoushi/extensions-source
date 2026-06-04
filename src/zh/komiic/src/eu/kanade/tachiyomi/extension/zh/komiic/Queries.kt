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

private fun buildQuery(query: String): String {
    val allCategory = categories.takeIf { it.isEmpty() }?.let { "allCategory { id name }" } ?: ""
    return query.trimIndent()
        .replace("#{body}", COMIC_BODY.trimIndent())
        .replace("#{category}", allCategory.trimIndent())
        .replace("%", "$")
}

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
      warnings
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

fun commonQuery(variables: ListingVariables): RequestBody {
    val operation = if (variables.pagination.orderBy == OrderBy.DATE_UPDATED) "recentUpdate" else "hotComics"
    val query = buildQuery(
        """
        query commonQuery(%pagination: Pagination!) {
          comics: $operation(pagination: %pagination) #{body}
          #{category}
        }
        """,
    )
    return buildRequestBody(query, variables.encode())
}

fun listingQuery(variables: ListingVariables): RequestBody {
    val query = buildQuery(
        """
        query comicByCategories(%categoryId: [ID!]!, %pagination: Pagination!) {
          comics: comicByCategories(categoryId: %categoryId, pagination: %pagination) #{body}
          #{category}
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
          #{category}
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

fun mangaDetailQuery(id: String): RequestBody {
    val query = buildQuery(
        """
        query comicById(%comicId: ID!) {
          comicById(comicId: %comicId) #{body}
        }
        """,
    )
    val variables = buildJsonObject {
        put("comicId", id)
    }
    return buildRequestBody(query, variables)
}

fun chaptersQuery(id: String): RequestBody {
    val query = buildQuery(
        """
        query chapterByComicId(%comicId: ID!) {
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
