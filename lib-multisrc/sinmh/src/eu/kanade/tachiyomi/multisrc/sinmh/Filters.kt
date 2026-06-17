package eu.kanade.tachiyomi.multisrc.sinmh

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    values: Array<String>,
    private val uriParts: Array<String>,
) : Filter.Select<String>(displayName, values) {
    fun toUriPart(): String = uriParts[state]
}

class SortFilter : Filter.Select<String>("排序方式", sortNames) {
    fun toUriPart(): String = sortKeys[state]
}

class Category(
    val name: String,
    val values: Array<String>,
    val uriParts: Array<String>,
) {
    fun toUriPartFilter() = UriPartFilter(name, values, uriParts)
}

internal val sortNames = arrayOf("按发布排序", "按发布排序(逆序)", "按更新排序", "按更新排序(逆序)", "按点击排序", "按点击排序(逆序)")
internal val sortKeys = arrayOf("post/", "-post/", "update/", "-update/", "click/", "-click/")
