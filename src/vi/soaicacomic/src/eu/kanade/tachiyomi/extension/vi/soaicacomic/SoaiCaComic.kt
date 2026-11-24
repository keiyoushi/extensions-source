package eu.kanade.tachiyomi.extension.vi.soaicacomic

import eu.kanade.tachiyomi.multisrc.koi.Koi
import keiyoushi.annotation.Source

@Source
abstract class SoaiCaComic : Koi() {
    override val popularPath = "xem-nhieu-nhat"
}
