package eu.kanade.tachiyomi.extension.en.mangadrama

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source

@Source
abstract class MangaDrama : InitManga() {

    override val mangaUrlDirectory = "manga"

    override val popularUrlSlug = "manga-ranking"

    override val latestUrlSlug = "recently-updated"

    override val authorLabel = "Author"

    override val artistLabel = "Illustrator"

    override val chapterPrefix = "Chapter"

    override val genreFilterName = "Genres"

    override val allGenresLabel = "All"
}
