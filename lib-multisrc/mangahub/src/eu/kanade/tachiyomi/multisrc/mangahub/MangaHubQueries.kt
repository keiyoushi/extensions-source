package eu.kanade.tachiyomi.multisrc.mangahub

class GraphQLTag

val SEARCH_QUERY = { mangaSource: String, query: String, genre: String, order: String, offset: Int ->
    """
        {
            search(x: $mangaSource, q: "$query", genre: "$genre", mod: $order, offset: $offset) {
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

val MANGA_DETAILS_QUERY = { mangaSource: String, slug: String ->
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

val MANGA_CHAPTER_LIST_QUERY = { mangaSource: String, slug: String ->
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

val PAGES_QUERY = { mangaSource: String, slug: String, number: Float ->
    """
        {
            chapter(x: $mangaSource, slug: "$slug", number: $number) {
                    pages
                }
        }
    """.trimIndent()
}
