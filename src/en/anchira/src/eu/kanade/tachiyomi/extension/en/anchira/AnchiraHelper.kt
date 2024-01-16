package eu.kanade.tachiyomi.extension.en.anchira

object AnchiraHelper {
    fun getPathFromUrl(url: String) = "${url.split("/").reversed()[1]}/${url.split("/").last()}"

    fun prepareTags(tags: List<Tag>, group: Boolean) = tags.map {
        if (it.namespace == null) {
            it.namespace = 6
        }
        it
    }
        .sortedBy { it.namespace }
        .map {
            val tag = it.name.lowercase()
            return@map if (group) {
                when (it.namespace) {
                    1 -> "artist:$tag"
                    2 -> "circle:$tag"
                    3 -> "parody:$tag"
                    4 -> "magazine:$tag"
                    else -> "tag:$tag"
                }
            } else {
                tag
            }
        }
        .joinToString(", ") { it }
}
