package eu.kanade.tachiyomi.extension.ar.mangatales

import eu.kanade.tachiyomi.multisrc.gmanga.ChapterRelease
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    val mangaReleases: List<ChapterRelease>,
)

@Serializable
class ReaderDto(
    val readerDataAction: ReaderData,
    val globals: Globals,
)

@Serializable
class Globals(
    val mediaKey: String,
)

@Serializable
class ReaderData(
    val readerData: ReaderChapter,
)

@Serializable
class ReaderChapter(
    val release: ReaderPages,
)

@Serializable
class ReaderPages(
    @SerialName("hq_pages") private val page: String,
) {
    val pages get() = page.split("\r\n")
}
