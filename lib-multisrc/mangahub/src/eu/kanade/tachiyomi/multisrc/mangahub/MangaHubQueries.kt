package eu.kanade.tachiyomi.multisrc.mangahub

import kotlinx.serialization.Serializable

private fun buildQuery(queryAction: () -> String) = queryAction().replace("%", "$")

val PAGES_QUERY = buildQuery {
    """
            query(%mangaSource: MangaSource, %slug: String!, %number: Float!) {
                chapter(x: %mangaSource, slug: %slug, number: %number) {
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
