package eu.kanade.tachiyomi.extension.en.frierenonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl

@Source
abstract class FrierenOnline : Madara() {
    override val supportsLatest = false
    override val supportsFilterFetching = false
    override val supportsRelatedMangas = false

    override fun getFilterList(data: JsonElement?) = FilterList()

    override val mangaDetailsSelectorTitle = ".about h1"
    override val mangaDetailsSelectorAuthor = "h5:contains(Author) + h4"
    override val mangaDetailsSelectorArtist = "h5:contains(Artist) + h4"
    override val mangaDetailsSelectorStatus = "h5:contains(Status) + h4"
    override val mangaDetailsSelectorDescription = ".synopsis"
    override val mangaDetailsSelectorThumbnail = ".cover_managa img"
    override val mangaDetailsSelectorGenre = ".tags a[rel=tag]"

    override fun chapterListSelector() = "li.m-chapter"
    override val chapterUrlSelector = "a:has(.chapter-content)"

    // Prepend /manga since mangaPath becomes / due to redirection
    override fun getChapterUrl(chapter: SChapter) = super.getChapterUrl(chapter).toHttpUrl().let {
        it.newBuilder().encodedPath("/manga${it.encodedPath}").build().toString()
    }
}
