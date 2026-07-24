package eu.kanade.tachiyomi.extension.en.warforrayuba.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GithubFileDto(
    val name: String,
    @SerialName("download_url") val downloadUrl: String,
)
