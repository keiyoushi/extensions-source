package eu.kanade.tachiyomi.extension.en.thewanderinginn

import eu.kanade.tachiyomi.source.model.SManga

enum class Comic(
    val title: String,
    val url: String,
    val categoryId: Int,
    val taxonomy: String,
    val termPath: String,
    private val thumbnail: String,
    private val author: String? = null,
    private val artist: String? = null,
    private val description: String? = null,
    private val status: Int = SManga.UNKNOWN,
) {
    MAIN(
        title = "The Wandering Inn",
        url = "/table-of-contents/",
        categoryId = 1,
        taxonomy = "tags",
        termPath = "tag",
        thumbnail = "https://webcomic.wanderinginn.com/wp-content/uploads/2024/12/TWIComicPoster2024_wLogo_Websize.png",
        author = "pirateaba",
        artist = "Dana \"ArtsyNada\" Johns",
        description = "This story begins with Erin Solstice, a young woman who is mysteriously transported to an unknown " +
            "world with unknown rules. With no idea where she is and nothing to help her, she stumbles upon an abandoned " +
            "inn and, in a twist of fate, becomes an [Innkeeper].",
        status = SManga.ONGOING,
    ),
    MINI_COMICS(
        title = "The Wandering Inn Mini Comics",
        url = "/category/mini-comics/",
        categoryId = 37,
        taxonomy = "collection",
        termPath = "collection",
        thumbnail = "https://webcomic.wanderinginn.com/wp-content/uploads/2025/11/BOCxTWI_thumbs_01_ty421-1.jpg",
    ),
    ;

    fun toSManga() = SManga.create().apply {
        url = this@Comic.url
        title = this@Comic.title
        thumbnail_url = thumbnail
        author = this@Comic.author
        artist = this@Comic.artist
        description = this@Comic.description
        status = this@Comic.status
    }
}
