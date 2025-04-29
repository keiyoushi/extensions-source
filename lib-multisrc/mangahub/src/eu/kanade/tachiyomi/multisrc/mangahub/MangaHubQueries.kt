package eu.kanade.tachiyomi.multisrc.mangahub

class GraphQLTag(
    val refreshUrl: String? = null,
)

val searchQuery = { mangaSource: String, query: String, genre: String, order: String, page: Int ->
    """
        {
            search(x: $mangaSource, q: "$query", genre: "$genre", mod: $order, offset: ${(page - 1) * 30}) {
                rows {
                    title,
                    author,
                    slug,
                    image,
                    genres,
                    latestChapter
                }
            }
        }
    """.trimIndent()
}

val mangaDetailsQuery = { mangaSource: String, slug: String ->
    """
        {
            manga(x: $mangaSource, slug: "$slug") {
                    title,
                    slug,
                    status,
                    image,
                    author,
                    artist,
                    genres,
                    description,
                    alternativeTitle
            }
        }
    """.trimIndent()
}

val mangaChapterListQuery = { mangaSource: String, slug: String ->
    """
        {
            manga(x: $mangaSource, slug: "$slug") {
                    slug,
                    chapters {
                        number,
                        title,
                        date
                    }
            }
        }
    """.trimIndent()
}

val pagesQuery = { mangaSource: String, slug: String, number: Float ->
    """
        {
            chapter(x: $mangaSource, slug: "$slug", number: $number) {
                    pages,
                    mangaID,
                    number,
                    manga {
                        slug
                    }
                }
        }
    """.trimIndent()
}
