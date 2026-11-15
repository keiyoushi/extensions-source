package eu.kanade.tachiyomi.extension.vi.soaicacomic

import eu.kanade.tachiyomi.multisrc.a3manga.A3Manga
import keiyoushi.annotation.Source

@Source
abstract class SoaiCaComic : A3Manga() {
    override val popularPath = "xem-nhieu-nhat"
}
