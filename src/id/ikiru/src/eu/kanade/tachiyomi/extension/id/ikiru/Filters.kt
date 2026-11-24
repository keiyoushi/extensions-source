package eu.kanade.tachiyomi.extension.id.ikiru

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UriFilter {
    fun addToUri(builder: HttpUrl.Builder)
}

class SortFilter :
    Filter.Select<String>("Urutkan", sortOptions.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val selected = sortOptions[state]
        builder.addQueryParameter("sortBy", selected.second)
        builder.addQueryParameter("sort", selected.third)
    }

    companion object {
        private val sortOptions = arrayOf(
            Triple("Populer", "popular", "desc"),
            Triple("Terbaru", "updated", "desc"),
            Triple("Judul A-Z", "title", "asc"),
            Triple("Judul Z-A", "title", "desc"),
            Triple("Paling Lama", "updated", "asc"),
        )
    }
}

// the search API takes single scalar values, not arrays
class TypeFilter :
    Filter.Select<String>("Tipe", typeOptions.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = typeOptions[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter("type", value)
        }
    }

    companion object {
        private val typeOptions = arrayOf(
            Pair("Semua", ""),
            Pair("Manga", "MANGA"),
            Pair("Manhwa", "MANHWA"),
            Pair("Manhua", "MANHUA"),
        )
    }
}

class StatusFilter :
    Filter.Select<String>("Status", statusOptions.map { it.first }.toTypedArray()),
    UriFilter {
    override fun addToUri(builder: HttpUrl.Builder) {
        val value = statusOptions[state].second
        if (value.isNotBlank()) {
            builder.addQueryParameter("status", value)
        }
    }

    companion object {
        private val statusOptions = arrayOf(
            Pair("Semua", ""),
            Pair("Ongoing", "ONGOING"),
            Pair("Completed", "COMPLETED"),
            Pair("Hiatus", "HIATUS"),
        )
    }
}

class Genre(name: String, val id: String) : Filter.CheckBox(name)

class GenreFilter(genres: List<Genre> = defaultGenres) :
    Filter.Group<Genre>("Genre", genres),
    UriFilter {
    // the search API expects repeated genre IDs (genre=<id>&genre=<id>), not slugs
    override fun addToUri(builder: HttpUrl.Builder) {
        state.filter { it.state }.forEach {
            builder.addQueryParameter("genre", it.id)
        }
    }
}

// switches the browse request to the "project" listing, so it is not part of the search URL
class ProjectFilter : Filter.Select<String>("Project", projectOptions.map { it.first }.toTypedArray()) {
    val isProject: Boolean get() = projectOptions[state].second

    companion object {
        private val projectOptions = arrayOf(
            Pair("Semua", false),
            Pair("Project", true),
        )
    }
}

// explicit genres (adult, hentai, lolicon, shotacon, smut) are left out, the extension is SFW
private val defaultGenres = listOf(
    Genre("Action", "01a10313-2bb6-722c-9497-925e7b423c24"),
    Genre("Adventure", "01a102f5-7098-77bc-9a1f-e1b12154fda0"),
    Genre("Comedy", "01a102f5-7099-7109-9bc2-9d5cb158ab54"),
    Genre("Doujinshi", "01a11053-1270-73b8-9314-5c6489b59d97"),
    Genre("Drama", "01a102ef-6ba9-723d-ba9a-5ebb04dc0929"),
    Genre("Ecchi", "01a106ea-26e6-768e-81cb-d9fc14505433"),
    Genre("Fantasy", "01a102f5-709a-732a-9656-05d3d83f712e"),
    Genre("Gender Bender", "01a106f3-9f51-774b-bc61-840e690e766a"),
    Genre("Harem", "01a10319-9753-77af-a80b-b0b4517e71f3"),
    Genre("Historical", "01a106e3-f9b0-7508-9191-d47788c973fc"),
    Genre("Horror", "01a10694-5e5e-7555-bd40-a3e145b5a7f2"),
    Genre("Josei", "01a10644-5385-7778-b347-9c53be2aae91"),
    Genre("Martial Arts", "01a106b8-81f1-76af-800c-3aa006417294"),
    Genre("Mature", "01a106e5-6106-765b-98ae-d4eb4b16357c"),
    Genre("Mecha", "01a10934-11af-7519-bcfe-571b79aed954"),
    Genre("Mystery", "01a10644-5385-7778-b347-a3bb2f354b11"),
    Genre("One-Shot", "01a106f8-8290-70ce-a46a-514575525feb"),
    Genre("Psychological", "01a10694-5e5f-768d-94fe-78d03c45373e"),
    Genre("Romance", "01a10319-9754-761b-9160-b1ab24806289"),
    Genre("School Life", "01a10319-9755-754e-92f3-a6c5cd8880eb"),
    Genre("Sci-fi", "01a10648-6664-759e-9be9-b9d14931e84e"),
    Genre("Seinen", "01a102ef-6baa-757f-aeaf-d47ae169d241"),
    Genre("Shoujo", "01a1067e-6943-720e-90c7-a3c6e442960b"),
    Genre("Shoujo Ai", "01a10725-ff69-74f8-8811-d4e12b2a6e66"),
    Genre("Shounen", "01a10313-2bb9-744f-b1ae-18f74252dd88"),
    Genre("Shounen Ai", "01a10f05-0394-769e-a9c0-cf23fd23271e"),
    Genre("Slice of Life", "01a102ef-6bab-72ea-82b6-aab5fc10bc80"),
    Genre("Sports", "01a10677-e30b-7298-b280-63596f2cb99d"),
    Genre("Supernatural", "01a102ef-6bac-72ca-abe5-f66a111a850a"),
    Genre("Tragedy", "01a10697-0e7a-75a3-97fc-a13f8a3fe53d"),
    Genre("Yaoi", "01a12040-7792-75fc-879c-3b14e50c120d"),
    Genre("Yuri", "01a10725-ff6a-74f8-a955-79e16845bda6"),
)
