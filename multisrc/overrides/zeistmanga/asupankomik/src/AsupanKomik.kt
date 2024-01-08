package eu.kanade.tachiyomi.extension.id.asupankomik

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class AsupanKomik : ZeistManga("Asupan Komik", "https://www.asupankomik.my.id", "id") {
    override val hasFilters = true
    override val pageListSelector = "div.check-box"
}
