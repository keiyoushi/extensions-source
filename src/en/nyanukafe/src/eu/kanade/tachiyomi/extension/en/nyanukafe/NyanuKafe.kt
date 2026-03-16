package eu.kanade.tachiyomi.extension.en.nyanukafe

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class NyanuKafe :
    Keyoapp(
        "Nyanu Kafe",
        "https://nyanukafe.com",
        "en",
    ) {
    override fun popularMangaSelector(): String = ".series-splide .splide__slide:not(.splide__slide--clone)"

    override val descriptionSelector: String = "div.grid > div#expand_content > p"
    override val statusSelector: String = "div.w-full.flex-wrap > div:eq(3) > div:last-child"
    override val authorSelector: String = "div.w-full.flex-wrap > div:eq(0) > div:last-child"
    override val artistSelector: String = "div.w-full.flex-wrap > div:eq(1) > div:last-child"
    override val typeSelector: String = "div.w-full.flex-wrap > div:eq(2) > div:last-child"
}
