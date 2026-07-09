package eu.kanade.tachiyomi.extension.ja.mangakingdom

import kotlinx.serialization.Serializable

@Serializable
class HeaderResponse(
    val numOfScenes: Int,
    val contentInfos: List<ContentInfo>,
    val dk: String?,
)

@Serializable
class ContentInfo(
    val name: String,
    val startSceneNo: Int,
    val endSceneNo: Int,
)

@Serializable
class ContentResponse(
    val scenes: List<Scene>,
)

@Serializable
class Scene(
    val sceneNo: Int,
    val images: List<SceneImage>,
)

@Serializable
class SceneImage(
    val width: Int,
    val height: Int,
    val key: Int,
    val imgBase64: String,
)
