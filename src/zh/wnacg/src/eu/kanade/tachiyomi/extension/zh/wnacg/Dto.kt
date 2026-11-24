package eu.kanade.tachiyomi.extension.zh.wnacg

import kotlinx.serialization.Serializable

@Serializable
class ReleasePageConfig(
    val sites: List<ReleasePageSite>,
)

@Serializable
class ReleasePageSite(
    val url: String,
)
