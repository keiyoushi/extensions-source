package eu.kanade.tachiyomi.extension.pt.hentaidatia

import kotlinx.serialization.Serializable

@Serializable
class TaxonomyDto(
    val name: String,
    val slug: String,
)

@Serializable
class FilterDataDto(
    val tags: List<TaxonomyDto>,
    val parodies: List<TaxonomyDto>,
)
