package eu.kanade.tachiyomi.extension.vi.mimihentai

import kotlinx.serialization.Serializable

@Serializable
class PageDTO(
    val pages: ArrayList<String> = arrayListOf(),
)
