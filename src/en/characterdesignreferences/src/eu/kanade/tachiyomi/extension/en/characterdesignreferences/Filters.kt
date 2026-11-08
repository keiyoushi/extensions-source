package eu.kanade.tachiyomi.extension.en.characterdesignreferences

import eu.kanade.tachiyomi.source.model.Filter

// Sections with image galleries; each is a run of numbered blog collections
enum class Section(
    val title: String,
    private val firstCollection: String,
    private val collectionPrefix: String?,
    val titleIsArtist: Boolean = false,
) {
    ART_OF_ANIMATION("Art of Animation", "art-of-animation-1", "art-of-animation-"),
    ARTIST_OF_THE_WEEK("Artist of the Week", "artists-of-the-week", "artist-of-the-week-", titleIsArtist = true),
    INTERVIEWS("Artist Interviews", "blog-interviews", "blog-interviews-", titleIsArtist = true),
    WINNERS("Challenge Winners", "winners1", "winners"),
    SPOTLIGHT("Challengers Spotlight", "challengers-spotlights", null),
    ;

    val isNumbered get() = collectionPrefix != null

    fun collection(number: Int): String? = if (number == 1) firstCollection else collectionPrefix?.let { "$it$number" }

    fun contains(collection: String): Boolean = collection == firstCollection ||
        (collectionPrefix != null && collection.startsWith(collectionPrefix) && collection.removePrefix(collectionPrefix).toIntOrNull() != null)

    companion object {
        fun of(collection: String): Section? = entries.firstOrNull { it.contains(collection) }
    }
}

class SectionFilter : Filter.Select<String>("Section", Section.entries.map { it.title }.toTypedArray()) {
    val selected get() = Section.entries[state]
}
