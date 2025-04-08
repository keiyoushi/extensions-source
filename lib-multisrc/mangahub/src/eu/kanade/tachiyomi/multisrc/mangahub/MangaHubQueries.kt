package eu.kanade.tachiyomi.multisrc.mangahub

import kotlinx.serialization.Serializable

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
                        slug,
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

@Serializable
data class ApiErrorMessages(
    val message: String,
)

@Serializable
data class ApiChapterPagesResponse(
    val data: ApiChapterData?,
    val errors: List<ApiErrorMessages>?,
)

@Serializable
data class ApiChapterData(
    val chapter: ApiChapter?,
)

@Serializable
data class ApiChapter(
    val pages: String,
)

@Serializable
data class ApiChapterPages(
    val p: String,
    val i: List<String>,
)

// Search, Popular, Latest
@Serializable
data class ApiSearchResponse(
    val data: ApiSearchObject,
)

@Serializable
data class ApiSearchObject(
    val search: ApiSearchResults,
)

@Serializable
data class ApiSearchResults(
    val rows: List<ApiMangaSearchItem>,
)

@Serializable
data class ApiMangaSearchItem(
    val title: String,
    val slug: String,
    val image: String,
    val author: String,
    val latestChapter: Float,
    val genres: String,
)

// Manga Details, Chapters
@Serializable
data class ApiMangaDetailsResponse(
    val data: ApiMangaObject,
)

@Serializable
data class ApiMangaObject(
    val manga: ApiMangaData,
)

@Serializable
data class ApiMangaData(
    val title: String?,
    val status: String?,
    val image: String?,
    val author: String?,
    val artist: String?,
    val genres: String?,
    val description: String?,
    val alternativeTitle: String?,
    val slug: String?,
    val chapters: List<ApiMangaChapterList>?,
)

@Serializable
data class ApiMangaChapterList(
    val number: Float,
    val title: String,
    val slug: String,
    val date: String,
)
