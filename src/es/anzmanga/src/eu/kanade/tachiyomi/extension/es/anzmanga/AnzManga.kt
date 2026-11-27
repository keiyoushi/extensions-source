package eu.kanade.tachiyomi.extension.es.anzmanga

import eu.kanade.tachiyomi.multisrc.mmrcms.MMRCMS
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class AnzManga : MMRCMS() {

    override val supportsAdvancedSearch = false

    // Most chapters host their pages on Google Drive (drive.google.com/uc?id=...). Drive answers 403 to an image
    // request marked Sec-Fetch-Site: cross-site, which is what a browser (and the browser headers) send from this
    // site, so those pages never loaded. The same file served from lh3.googleusercontent.com/d/<id> is not blocked.
    override fun pageListParse(document: Document): List<Page> = super.pageListParse(document).map { page ->
        val id = page.imageUrl?.let { DRIVE_ID.find(it)?.groupValues?.get(1) }
        if (id == null) page else Page(page.index, imageUrl = "https://lh3.googleusercontent.com/d/$id")
    }

    companion object {
        private val DRIVE_ID = Regex("""drive\.google\.com/(?:uc\?(?:.*&)?id=|file/d/|open\?id=)([A-Za-z0-9_-]{10,})""")
    }
}
