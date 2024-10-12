package eu.kanade.tachiyomi.extension.en.rezoscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class RezoScans : Keyoapp(
    "Rezo Scans",
    "https://rezoscans.com",
    "en",
) {
    override fun chapterListSelector() = "${super.chapterListSelector()}:not(:has(img[src^='/assets/images/Coin.svg']))"
}
