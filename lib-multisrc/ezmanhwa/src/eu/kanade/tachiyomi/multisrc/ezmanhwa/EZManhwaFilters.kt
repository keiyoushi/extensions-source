package eu.kanade.tachiyomi.multisrc.ezmanhwa

import eu.kanade.tachiyomi.source.model.Filter

class EZManhwaSortFilter : Filter.Select<String>("Sort", DISPLAY) {
    val value get() = VALUES[state]
    companion object {
        private val VALUES = arrayOf("latest", "popular", "newest", "alphabetical")
        val DISPLAY = arrayOf("Latest", "Popular", "Newest", "Alphabetical")
    }
}

class EZManhwaStatusFilter : Filter.Select<String>("Status", DISPLAY) {
    val value get() = VALUES[state]
    companion object {
        private val VALUES = arrayOf("", "ONGOING", "COMPLETED", "HIATUS", "DROPPED")
        val DISPLAY = arrayOf("All", "Ongoing", "Completed", "Hiatus", "Dropped")
    }
}

class EZManhwaTypeFilter : Filter.Select<String>("Type", DISPLAY) {
    val value get() = VALUES[state]
    companion object {
        private val VALUES = arrayOf("", "MANGA", "MANHWA", "MANHUA")
        val DISPLAY = arrayOf("All", "Manga", "Manhwa", "Manhua")
    }
}
