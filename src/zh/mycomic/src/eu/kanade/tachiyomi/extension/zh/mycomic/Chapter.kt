package eu.kanade.tachiyomi.extension.zh.mycomic

import kotlinx.serialization.Serializable

@Serializable
data class Chapter(val id: Long, val title: String)
