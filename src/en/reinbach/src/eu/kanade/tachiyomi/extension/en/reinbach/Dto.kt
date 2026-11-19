package eu.kanade.tachiyomi.extension.en.reinbach

import kotlinx.serialization.Serializable

@Serializable
class Rendered(
    val rendered: String,
)

@Serializable
class WpCategory(
    val id: Int,
    val name: String,
    val slug: String,
    val count: Int,
    val parent: Int,
    val link: String,
)

/**
 * `/posts?categories=…&search=Chapter&_fields=id,date,title,link`
 */
@Serializable
class WpPostMarker(
    val id: Int,
    val date: String,
    val title: Rendered,
    val link: String,
)

/**
 * `/posts?categories=…&after=…&_fields=id,title,content`
 */
@Serializable
class WpPostPage(
    val id: Int,
    val title: Rendered,
    val content: Rendered,
)

/**
 * Requests made with `_fields=content` only.
 */
@Serializable
class WpContent(
    val content: Rendered,
)
