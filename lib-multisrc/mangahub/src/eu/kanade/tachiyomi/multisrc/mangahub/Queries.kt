package eu.kanade.tachiyomi.multisrc.mangahub

import kotlinx.serialization.json.JsonPrimitive

val searchQuery = { mangaSource: String, query: String, genre: String, order: String, page: Int ->
    val escapedQuery = JsonPrimitive(query).toString().removeSurrounding("\"")
    """
        {
            search(x: $mangaSource, q: "$escapedQuery", genre: "$genre", mod: $order, offset: ${(page - 1) * 30}) {
                rows {
                    title,
                    slug,
                    image
                }
            }
        }
    """.trimIndent()
}

val mangaQuery = { mangaSource: String, slug: String ->
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
                    alternativeTitle,
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
                    number
                }
        }
    """.trimIndent()
}
