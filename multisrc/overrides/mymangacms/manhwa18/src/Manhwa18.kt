package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.multisrc.mymangacms.MyMangaCMS
import eu.kanade.tachiyomi.source.model.FilterList

class Manhwa18 : MyMangaCMS("Manhwa18", "https://manhwa18.com", "en") {

    // Migrated from FMReader to MyMangaCMS.
    override val versionId = 2

    override val parseAuthorString = "Author"
    override val parseAlternativeNameString = "Other name"
    override val parseAlternative2ndNameString = "Doujinshi"
    override val parseStatusString = "Status"
    override val parseStatusOngoingStringLowerCase = "on going"
    override val parseStatusOnHoldStringLowerCase = "on hold"
    override val parseStatusCompletedStringLowerCase = "completed"

    override fun getFilterList(): FilterList = FilterList(
        Author("Author"),
        Status(
            "Status",
            "All",
            "Ongoing",
            "On hold",
            "Completed",
        ),
        Sort(
            "Order",
            "A-Z",
            "Z-A",
            "Latest update",
            "New manhwa",
            "Most view",
            "Most like",
        ),
        GenreList(getGenreList(), "Genre"),
    )

    // To populate this list:
    // console.log([...document.querySelectorAll("div.search-gerne_item")].map(elem => `Genre("${elem.textContent.trim()}", ${elem.querySelector("label").getAttribute("data-genre-id")}),`).join("\n"))
    override fun getGenreList() = listOf(
        Genre("Adult", 4),
        Genre("Doujinshi", 9),
        Genre("Harem", 17),
        Genre("Manga", 24),
        Genre("Manhwa", 26),
        Genre("Mature", 28),
        Genre("NTR", 33),
        Genre("Romance", 36),
        Genre("Webtoon", 57),
        Genre("Action", 59),
        Genre("Comedy", 60),
        Genre("BL", 61),
        Genre("Horror", 62),
        Genre("Raw", 63),
        Genre("Uncensore", 64),
    )

    override fun dateUpdatedParser(date: String): Long =
        runCatching { dateFormatter.parse(date.substringAfter(" - "))?.time }.getOrNull() ?: 0L
}
