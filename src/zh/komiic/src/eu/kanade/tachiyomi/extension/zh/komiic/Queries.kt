package eu.kanade.tachiyomi.extension.zh.komiic

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
}

val QUERY_HOT_COMICS: String = buildQuery {
    """
        query hotComics(%pagination: Pagination!) {
          result: hotComics(pagination: %pagination) {
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
        }
    """
}

val QUERY_RECENT_UPDATE: String = buildQuery {
    """
        query recentUpdate(%pagination: Pagination!) {
          result: recentUpdate(pagination: %pagination) {
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
        }
    """
}

val QUERY_SEARCH: String = buildQuery {
    """
        query searchComicAndAuthorQuery(%keyword: String!) {
          result: searchComicsAndAuthors(keyword: %keyword) {
            result: comics {
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
          }
        }
    """
}

val QUERY_CHAPTER: String = buildQuery {
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

val QUERY_COMIC_BY_ID = buildQuery {
    """
        query comicById(%comicId: ID!) {
          result: comicById(comicId: %comicId) {
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

// val QUERY_API_LIMIT = buildQuery {
//     """
//         query reachedImageLimit {
//           reachedImageLimit
//         }
//     """
// }
