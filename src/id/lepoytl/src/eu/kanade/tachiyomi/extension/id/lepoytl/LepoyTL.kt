package eu.kanade.tachiyomi.extension.id.lepoytl

import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga

class LepoyTL : ZeistManga("LepoyTL", "https://www.lepoytl.my.id", "id") {

    // =========================== Manga Details ============================
    override val mangaDetailsSelector = "main"
    override val mangaDetailsSelectorGenres = "aside dl a[rel=tag]"
    override val mangaDetailsSelectorInfo = "#extra-info dl"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    // =============================== Pages ================================
    override val pageListSelector = "#reader"
}
