package eu.kanade.tachiyomi.extension.all.hentairun

import eu.kanade.tachiyomi.source.model.Page
import kotlinx.serialization.Serializable

// The reader's page list, from the data of its page
@Serializable
class Reader(
    private val galleryId: String,
    private val pages: List<ReaderPage>,
    private val imgDir: String,
    private val server: Int,
) {
    fun toPages(host: String) = pages.mapIndexed { index, page ->
        Page(index, imageUrl = "https://m$server.$host/$imgDir/$galleryId/${page.page}.${page.ext}")
    }
}

@Serializable
class ReaderPage(val page: Int, val ext: String)
