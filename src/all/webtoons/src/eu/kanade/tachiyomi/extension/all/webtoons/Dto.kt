package eu.kanade.tachiyomi.extension.all.webtoons

import kotlinx.serialization.Serializable

@Serializable
class MotionToonResponse(
    val assets: MotionToonAssets,
)

@Serializable
class MotionToonAssets(
    val images: Map<String, String>,
)
