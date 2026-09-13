package eu.kanade.tachiyomi.multisrc.origines

import kotlinx.serialization.Serializable

@Serializable
class CatalogueResponse(
    val data: CatalogueData,
)

@Serializable
class CatalogueData(
    val html: String = "",
    val more: Boolean = false,
)
