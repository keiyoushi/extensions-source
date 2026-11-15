package eu.kanade.tachiyomi.extension.ja.younganimal

import eu.kanade.tachiyomi.multisrc.comiciviewer.ComiciViewer
import keiyoushi.annotation.Source

@Source
abstract class YoungAnimal : ComiciViewer() {
    override val extraFilterOptions = listOf(
        "グラビア" to "/category/gravure",
    )
}
