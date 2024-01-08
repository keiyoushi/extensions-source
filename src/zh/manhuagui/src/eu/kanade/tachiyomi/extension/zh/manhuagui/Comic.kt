package eu.kanade.tachiyomi.extension.zh.manhuagui

import kotlinx.serialization.Serializable

@Serializable
data class Comic(
    val bid: Int? = 0,
    val block_cc: String? = "",
    val bname: String? = "",
    val bpic: String? = "",
    val cid: Int? = 0,
    val cname: String? = "",
    val files: List<String?>? = listOf(),
    val finished: Boolean? = false,
    val len: Int? = 0,
    val nextId: Int? = 0,
    val path: String? = "",
    val prevId: Int? = 0,
    val sl: Sl? = Sl(),
    val status: Int? = 0,
)
