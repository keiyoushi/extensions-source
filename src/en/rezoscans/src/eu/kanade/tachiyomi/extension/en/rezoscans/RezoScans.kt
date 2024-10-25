package eu.kanade.tachiyomi.extension.en.rezoscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class RezoScans : Keyoapp(
    "Rezo Scans",
    "https://rezoscans.com",
    "en",
) {
    override val cdnUrl = "https://3xfsjdlc.is1.buzz/uploads"
}
