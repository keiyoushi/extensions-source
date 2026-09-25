package eu.kanade.tachiyomi.extension.en.webtoonxyz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class WebtoonXYZ : Madara() {
    override val chapterDateFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
    override val mangaSubString = "read"
    override val genreDirectory = "webtoon-genre"
    override val sendViewCount = false

    private val thumbnailOriginalUrlRegex = Regex("-\\d+x\\d+(\\.[a-zA-Z]+)$")

    override fun processThumbnail(url: String?, fromSearch: Boolean) = url?.replace(thumbnailOriginalUrlRegex, "$1")
}
