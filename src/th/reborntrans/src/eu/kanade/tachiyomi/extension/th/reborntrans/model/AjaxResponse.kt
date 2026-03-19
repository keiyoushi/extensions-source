package eu.kanade.tachiyomi.extension.th.reborntrans.model

import kotlinx.serialization.Serializable

@Serializable
class AjaxResponse(
    val success: Boolean,
    val data: AjaxData? = null,
)

@Serializable
class AjaxData(
    val html: String,
)
