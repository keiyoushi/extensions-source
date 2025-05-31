package eu.kanade.tachiyomi.extension.all.snowmtl.translator.google

import okhttp3.Response

class GoogleTranslatorDto

data class Translated(
    val from: String,
    val to: String,
    val origin: String,
    val text: String,
    val pronunciation: String,
    val extraData: Map<String, Any?>,
    val response: Response,
)
