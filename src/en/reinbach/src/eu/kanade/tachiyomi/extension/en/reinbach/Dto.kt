package eu.kanade.tachiyomi.extension.en.reinbach

import kotlinx.serialization.Serializable

@Serializable
class Rendered(
    val rendered: String = "",
)

@Serializable
class WpCategory(
    val id: Int = 0,
    val name: String = "",
    val slug: String = "",
    val count: Int = 0,
    val parent: Int = 0,
    val link: String = "",
)

@Serializable
class WpPost(
    val id: Int = 0,
    val date: String = "",
    val link: String = "",
    val title: Rendered = Rendered(),
    val content: Rendered = Rendered(),
)
