package eu.kanade.tachiyomi.extension.en.nyanukafe

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class NyanuKafe : Keyoapp(
    "Nyanu Kafe",
    "https://nyanukafe.com",
    "en",
) {
    override val descriptionSelector: String = "div.grid > div.overflow-hidden > p"
    override val statusSelector: String = "div[alt=Status]"
    override val authorSelector: String = "div[alt=Author]"
    override val artistSelector: String = "div[alt=Artist]"
    override val genreSelector: String = "div[alt='Series Type']"
}
