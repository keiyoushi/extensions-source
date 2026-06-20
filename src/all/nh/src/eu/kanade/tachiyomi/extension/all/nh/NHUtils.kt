package eu.kanade.tachiyomi.extension.all.nh

object NHUtils {
    fun getArtists(tags: List<Tag>): String =
        tags.filter { it.type == "artist" }.joinToString(", ") { it.name }

    fun getGroups(tags: List<Tag>): String? =
        tags.filter { it.type == "group" }.joinToString(", ") { it.name }.takeIf { it.isNotBlank() }

    fun getTagDescription(tags: List<Tag>): String {
        val grouped = tags.groupBy { it.type }
        return buildString {
            grouped["category"]?.joinToString { it.name }?.let { append("Categories: ", it, "\n") }
            grouped["parody"]?.joinToString { it.name }?.let { append("Parodies: ", it, "\n") }
            grouped["character"]?.joinToString { it.name }?.let { append("Characters: ", it, "\n\n") }
        }
    }

    fun getTags(tags: List<Tag>): String =
        tags.filter { it.type == "tag" }.joinToString(", ") { it.name }
}
