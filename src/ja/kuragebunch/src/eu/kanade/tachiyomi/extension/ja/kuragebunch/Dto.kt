package eu.kanade.tachiyomi.extension.ja.kuragebunch

import eu.kanade.tachiyomi.multisrc.gigaviewer.GigaViewerPage
import kotlinx.serialization.Serializable

@Serializable
class PageListDto(
    val readableProduct: ReadableProductDto,
)

@Serializable
class ReadableProductDto(
    val pageStructure: PageStructureDto,
)

@Serializable
class PageStructureDto(
    val pages: List<GigaViewerPage>,
    val choJuGiga: String? = null,
)
