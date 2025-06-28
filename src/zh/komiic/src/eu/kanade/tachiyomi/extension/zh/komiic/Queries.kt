package eu.kanade.tachiyomi.extension.zh.komiic

private fun buildQuery(body: String = "", queryAction: () -> String): String {
    return queryAction().trimIndent()
        .replace("#{body}", body.trimIndent())
        .replace("%", "$")
}

const val COMIC_BODY =
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

val QUERY_HOT_COMICS = buildQuery(COMIC_BODY) {
    """
    query hotComics(%pagination: Pagination!) {
      result: hotComics(pagination: %pagination) #{body}
    }
    """
}

val QUERY_RECENT_UPDATE = buildQuery(COMIC_BODY) {
    """
    query recentUpdate(%pagination: Pagination!) {
      result: recentUpdate(pagination: %pagination) #{body}
    }
    """
}

val QUERY_SEARCH = buildQuery(COMIC_BODY) {
    """
    query searchComicAndAuthorQuery(%keyword: String!) {
      result: searchComicsAndAuthors(keyword: %keyword) {
        result: comics #{body}
      }
    }
    """
}

val QUERY_COMIC_BY_ID = buildQuery(COMIC_BODY) {
    """
    query comicById(%comicId: ID!) {
      result: comicById(comicId: %comicId) #{body}
    }
    """
}

val QUERY_CHAPTER = buildQuery {
    """
    query chapterByComicId(%comicId: ID!) {
      result: chaptersByComicId(comicId: %comicId) {
        id
        serial
        type
        size
        dateUpdated
      }
    }
    """
}

val QUERY_PAGE_LIST = buildQuery {
    """
    query imagesByChapterId(%chapterId: ID!) {
      result: imagesByChapterId(chapterId: %chapterId) {
        id
        kid
        height
        width
      }
    }
    """
}

val QUERY_API_LIMIT = buildQuery { "query reachedImageLimit { result: reachedImageLimit }" }
