package eu.kanade.tachiyomi.extension.th.nekopost.model

import kotlinx.serialization.Serializable

@Serializable
data class EditorProject(
    val pid: Int,
    val projectName: String,
    val projectType: String,
    val status: Int,
    val aliasName: String? = null,
    val website: String? = null,
    val authorName: String? = null,
    val artistName: String? = null,
    val coverVersion: Int? = null,
)
