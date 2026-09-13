package eu.kanade.tachiyomi.extension.pt.horahentai

import eu.kanade.tachiyomi.source.model.Filter
import kotlinx.serialization.Serializable

@Serializable
internal class TagDto(
    val name: String,
    val slug: String,
)

internal open class SelectFilter(
    name: String,
    private val options: List<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun selected(): String = options[state].second
}

internal class CategoryFilter :
    SelectFilter(
        "Categoria",
        listOf(
            "Todas" to "",
            "Doujinshi" to "doujinshi",
            "Mangá Hentai" to "manga-hentai",
            "Comics" to "comics",
            "Sem Censura" to "hentai-sem-censura",
        ),
    )

internal class TagFilter(tags: List<TagDto>) :
    SelectFilter(
        "Tag",
        listOf("Todas" to "") + tags.map { it.name to it.slug },
    )
