package eu.kanade.tachiyomi.extension.zh.komiic

enum class Query {
    HOT_COMICS {
        override val operation = "hotComics"
        override val body = buildQuery(comicBody) {
            """
            query hotComics(%pagination: Pagination!) {
              result: hotComics(pagination: %pagination) #{body}
            }
            """
        }
    },
    RECENT_UPDATE {
        override val operation = "recentUpdate"
        override val body = buildQuery(comicBody) {
            """
            query recentUpdate(%pagination: Pagination!) {
              result: recentUpdate(pagination: %pagination) #{body}
            }
            """
        }
    },
    SEARCH {
        override val operation = "searchComicAndAuthorQuery"
        override val body = buildQuery(comicBody) {
            """
            query searchComicAndAuthorQuery(%keyword: String!) {
              result: searchComicsAndAuthors(keyword: %keyword) {
                result: comics #{body}
              }
            }
            """
        }
    },
    COMIC_BY_CATEGORIES {
        override val operation = "comicByCategories"
        override val body = buildQuery(comicBody) {
            """
            query comicByCategories(%categoryId: [ID!]!, %pagination: Pagination!) {
              result: comicByCategories(categoryId: %categoryId, pagination: %pagination) #{body}
            }
            """
        }
    },
    COMIC_BY_ID {
        override val operation = "comicById"
        override val body = buildQuery(comicBody) {
            """
            query comicById(%comicId: ID!) {
              result: comicById(comicId: %comicId) #{body}
            }
            """
        }
    },
    CHAPTERS_BY_COMIC_ID {
        override val operation = "chapterByComicId"
        override val body = buildQuery {
            """
            query chapterByComicId(%comicId: ID!) {
              result: chaptersByComicId(comicId: %comicId) {
                id
                serial
                type
                size
                dateCreated
              }
            }
            """
        }
    },
    IMAGES_BY_CHAPTER_ID {
        override val operation = "imagesByChapterId"
        override val body = buildQuery {
            """
            query imagesByChapterId(%chapterId: ID!) {
              result1: reachedImageLimit,
              result2: imagesByChapterId(chapterId: %chapterId) {
                id
                kid
                height
                width
              }
            }
            """
        }
    }, ;

    abstract val body: String
    abstract val operation: String
    val comicBody =
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

    fun buildQuery(body: String = "", queryAction: () -> String): String {
        return queryAction().trimIndent()
            .replace("#{body}", body.trimIndent())
            .replace("%", "$")
    }
}
