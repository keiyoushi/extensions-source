package eu.kanade.tachiyomi.extension.vi.teamlanhlung

import eu.kanade.tachiyomi.multisrc.a3manga.A3Manga
import keiyoushi.annotation.Source

@Source
abstract class TeamLanhLung : A3Manga() {
    override val popularPath = "xem-nhieu-nhat"
}
