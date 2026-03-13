package eu.kanade.tachiyomi.extension.th.reborntrans.model

import kotlinx.serialization.Serializable

@Serializable
data class AjaxResponse(
    val success: Boolean,
    val data: AjaxData? = null,
)

@Serializable
data class AjaxData(
    val html: String,
)
