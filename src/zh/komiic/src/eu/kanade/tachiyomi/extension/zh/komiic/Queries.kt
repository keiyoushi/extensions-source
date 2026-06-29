package eu.kanade.tachiyomi.extension.zh.komiic

import eu.kanade.tachiyomi.source.model.MangasPage
import keiyoushi.utils.graphQLBody
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.RequestBody

private fun buildQuery(query: String): String {
    val allCategory = categories.takeIf { it.isEmpty() }?.let { "allCategory { id name }" } ?: ""
    return query.replace("#{category}", allCategory).trimIndent()
}

private val COMIC_BODY =
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
    """.trimIndent()

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
        $$"""
        query commonQuery($pagination: Pagination!) {
          comics: $$operation(pagination: $pagination) $$COMIC_BODY
          #{category}
        }
        """,
    )
    return graphQLBody(query, "commonQuery", variables.encode())
}

fun listingQuery(variables: ListingVariables): RequestBody {
    val query = buildQuery(
        $$"""
        query comicByCategories($categoryId: [ID!]!, $pagination: Pagination!) {
          comics: comicByCategories(categoryId: $categoryId, pagination: $pagination) $$COMIC_BODY
          #{category}
        }
        """,
    )
    return graphQLBody(query, "comicByCategories", variables.encode())
}

fun searchQuery(keyword: String): RequestBody {
    val query = buildQuery(
        $$"""
        query searchComicsAndAuthor($keyword: String!) {
          searchComicsAndAuthors(keyword: $keyword) {
            comics $$COMIC_BODY
          }
          #{category}
        }
        """,
    )
    val variables = buildJsonObject {
        put("keyword", keyword)
    }
    return graphQLBody(query, "searchComicsAndAuthor", variables)
}

fun idsQuery(id: String): RequestBody {
    val query = buildQuery(
        $$"""
        query comicByIds($comicIds: [ID]!) {
          comics: comicByIds(comicIds: $comicIds) $$COMIC_BODY
        }
        """,
    )
    val variables = buildJsonObject {
        putJsonArray("comicIds") { add(id) }
    }
    return graphQLBody(query, "comicByIds", variables)
}

fun mangaDetailQuery(id: String): RequestBody {
    val query = buildQuery(
        $$"""
        query comicById($comicId: ID!) {
          comicById(comicId: $comicId) $$COMIC_BODY
        }
        """,
    )
    val variables = buildJsonObject {
        put("comicId", id)
    }
    return graphQLBody(query, "comicById", variables)
}

fun chapterListQuery(id: String): RequestBody {
    val query = buildQuery(
        $$"""
        query chapterByComicId($comicId: ID!) {
          chaptersByComicId(comicId: $comicId) {
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
    return graphQLBody(query, "chapterByComicId", variables)
}

fun pageListQuery(chapterId: String): RequestBody {
    val query = buildQuery(
        $$"""
        query imagesByChapterId($chapterId: ID!) {
          imagesByChapterId(chapterId: $chapterId) {
            kid
          }
        }
        """,
    )
    val variables = buildJsonObject {
        put("chapterId", chapterId)
    }
    return graphQLBody(query, "imagesByChapterId", variables)
}
