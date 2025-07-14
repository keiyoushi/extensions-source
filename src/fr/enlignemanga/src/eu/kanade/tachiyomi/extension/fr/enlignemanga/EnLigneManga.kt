package eu.kanade.tachiyomi.extension.fr.enlignemanga

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig
import eu.kanade.tachiyomi.source.model.SManga

class EnLigneManga : MCCMS(
    "En Ligne Manga",
    "https://www.enlignemanga.com",
    "fr",
    MCCMSConfig(lazyLoadImageAttr = "src"),
) {
    override fun SManga.cleanup() = apply {
        title = title.substringBeforeLast(" ligne")
    }
}
