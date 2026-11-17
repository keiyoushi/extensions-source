package eu.kanade.tachiyomi.multisrc.pam

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** What page.js reads: a challenge, an error status, or the page reader's result once the page is ready. */
@Serializable
internal class PageRead(
    val challenge: Boolean = false,
    val status: Int? = null,
    val result: JsonObject? = null,
)

@Serializable
internal class LibraryPage(
    val items: List<Card>,
    val hasNextPage: Boolean,
) {
    @Serializable
    class Card(
        private val path: String,
        private val title: String,
        private val cover: String,
    ) {
        fun toSManga(coverFragment: String) = SManga.create().apply {
            url = path.removePrefix("/serie/")
            title = this@Card.title
            thumbnail_url = "$cover#$coverFragment"
        }
    }
}

@Serializable
internal class SeriePage(
    val title: String,
    val cover: String?,
    val synopsis: String?,
    val info: Info?,
    val infoTab: Point?,
    val types: List<String>,
    val teams: List<String>,
    val rating: String?,
    val chapterCount: Int?,
    val page: Int,
    val chapters: List<Row>,
    val next: Point?,
    val viewport: Viewport,
    val scale: Float,
) {
    @Serializable
    class Info(
        val altTitles: List<String>,
        val author: List<String>,
        val artist: List<String>,
        val year: String?,
        val status: String?,
        val genres: List<String>,
    )

    @Serializable
    class Row(
        val path: String,
        val name: String,
        val title: String?,
        val premium: Boolean,
        val account: Boolean,
        val age: String?,
    )
}

@Serializable
internal class Point(val x: Float, val y: Float, val clear: Boolean)

@Serializable
internal class Viewport(val width: Float, val height: Float)

@Serializable
internal class ReaderPage(
    val scale: Float,
    val height: Float,
    val clear: List<Band>,
    val plates: List<Plate>,
    val gate: String? = null,
    val dismiss: Point? = null,
) {
    @Serializable
    class Band(val top: Float, val bottom: Float)

    @Serializable
    class Plate(
        val top: Float,
        val left: Float,
        val width: Float,
        val height: Float,
        val drawn: Boolean,
        val canvasWidth: Int,
        val retry: Point?,
    )
}
