package eu.kanade.tachiyomi.extension.tr.summertoon

import kotlinx.serialization.Serializable

@Serializable
class AjaxDto<T>(val data: T)

@Serializable
class DecisionDto(val grant: String)

@Serializable
class ManifestDto(val images: List<String>)
