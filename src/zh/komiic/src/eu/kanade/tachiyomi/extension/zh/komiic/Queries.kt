package eu.kanade.tachiyomi.extension.zh.komiic

private fun buildQuery(queryAction: () -> String): String {
    return queryAction()
        .trimIndent()
        .replace("%", "$")
}

val QUERY_HOT_COMICS: String = buildQuery {
    """
        query hotComics(%pagination: Pagination!) {
          hotComics(pagination: %pagination) {
            id
            title
            status
            year
            imageUrl
            authors {
              id
              name
              __typename
            }
            categories {
              id
              name
              __typename
            }
            dateUpdated
            monthViews
            views
            favoriteCount
            lastBookUpdate
            lastChapterUpdate
            __typename
          }
        }
    """
}

val QUERY_RECENT_UPDATE: String = buildQuery {
    """
        query recentUpdate(%pagination: Pagination!) {
          recentUpdate(pagination: %pagination) {
            id
            title
            status
            year
            imageUrl
            authors {
              id
              name
              __typename
            }
            categories {
              id
              name
              __typename
            }
            dateUpdated
            monthViews
            views
            favoriteCount
            lastBookUpdate
            lastChapterUpdate
            __typename
          }
        }
    """
}

val QUERY_SEARCH: String = buildQuery {
    """
        query searchComicAndAuthorQuery(%keyword: String!) {
          searchComicsAndAuthors(keyword: %keyword) {
            comics {
              id
              title
              status
              year
              imageUrl
              authors {
                id
                name
                __typename
              }
              categories {
                id
                name
                __typename
              }
              dateUpdated
              monthViews
              views
              favoriteCount
              lastBookUpdate
              lastChapterUpdate
              __typename
            }
            authors {
              id
              name
              chName
              enName
              wikiLink
              comicCount
              views
              __typename
            }
            __typename
          }
        }
    """
}

val QUERY_CHAPTER: String = buildQuery {
    """
        query chapterByComicId(%comicId: ID!) {
          chaptersByComicId(comicId: %comicId) {
            id
            serial
            type
            dateCreated
            dateUpdated
            size
            __typename
          }
        }
    """
}

val QUERY_COMIC_BY_ID = buildQuery {
    """
        query comicById(%comicId: ID!) {
          comicById(comicId: %comicId) {
            id
            title
            status
            year
            imageUrl
            authors {
              id
              name
              __typename
            }
            categories {
              id
              name
              __typename
            }
            dateCreated
            dateUpdated
            views
            favoriteCount
            lastBookUpdate
            lastChapterUpdate
            __typename
          }
        }
    """
}

val QUERY_PAGE_LIST = buildQuery {
    """
        query imagesByChapterId(%chapterId: ID!) {
          imagesByChapterId(chapterId: %chapterId) {
            id
            kid
            height
            width
            __typename
          }
        }
    """
}

val QUERY_API_LIMIT = buildQuery {
    """
        query getImageLimit {
          getImageLimit {
            limit
            usage
            resetInSeconds
            __typename
          }
        }
    """
}
