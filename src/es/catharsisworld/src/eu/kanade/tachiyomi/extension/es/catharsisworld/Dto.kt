package eu.kanade.tachiyomi.extension.es.catharsisworld

import eu.kanade.tachiyomi.multisrc.catharsis.MangaListDto
import eu.kanade.tachiyomi.multisrc.catharsis.Point
import kotlinx.serialization.Serializable

@Serializable
internal class SeriesPage(
    val scale: Float,
    val id: String?,
    val height: Float,
    val width: Float,
    val tap: Point?,
    val title: String?,
    val altTitle: String?,
    val badges: List<String> = emptyList(),
    val description: String?,
    val cover: String?,
    val scans: List<String> = emptyList(),
    val author: String?,
    val rows: List<Row> = emptyList(),
    val more: Point?,
    val count: Int = -1,
    val rating: Float?,
    val votes: Int?,
)

@Serializable
internal class ListingPage(
    val scale: Float,
    val width: Float,
    val height: Float,
    val tap: Point?,
    val list: MangaListDto?,
    val ids: Map<String, String?> = emptyMap(),
)

@Serializable
internal class GenrePanel(
    val scale: Float,
    val width: Float,
    val height: Float,
    val tap: Point?,
    val names: List<String>?,
    val ids: Map<String, String> = emptyMap(),
)

// A genre of the filter panel, with its id when the site's cached lists showed it
@Serializable
internal class GenreOption(val name: String, val id: String?)

@Serializable
internal class Row(val href: String, val heading: String, val title: String?, val age: String?)
