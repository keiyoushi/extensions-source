package eu.kanade.tachiyomi.multisrc.bakkin

@kotlinx.serialization.Serializable
internal data class Series(
    val dir: String,
    val name: String,
    val author: String?,
    val status: String?,
    val thumb: String?,
    val volumes: List<Volume>,
) : Iterable<Chapter> {
    override fun iterator() = volumes.flatMap { vol ->
        vol.map {
            it.copy(
                name = "$vol - $it",
                dir = "$dir/${vol.dir}/${it.dir}",
            )
        }
    }.iterator()

    val cover: String
        get() = thumb ?: "static/nocover.png"

    override fun toString() = name.ifEmpty { dir }
}

@kotlinx.serialization.Serializable
internal data class Volume(
    val dir: String,
    val name: String,
    val chapters: List<Chapter>,
) : Iterable<Chapter> by chapters {
    override fun toString() = name.ifEmpty { dir }
}

@kotlinx.serialization.Serializable
internal data class Chapter(
    val dir: String,
    val name: String,
    val pages: List<String>,
) : Iterable<String> by pages {
    val number: Float
        get() = dir.substringAfterLast('c').toFloatOrNull() ?: -1f

    override fun toString() = name.ifEmpty { dir }
}
