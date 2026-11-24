package eu.kanade.tachiyomi.extension.en.mangadrama

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga
import keiyoushi.annotation.Source
import java.time.ZoneId
import java.time.ZoneOffset

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

    override val altTitleLabel = "Alternative title"

    override val siteZone: ZoneId = ZoneOffset.ofHours(8)
}
