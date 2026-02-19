package eu.kanade.tachiyomi.extension.fr.frmanga

import eu.kanade.tachiyomi.multisrc.mccms.MCCMS
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSConfig

class FRManga :
    MCCMS(
        "FR Manga",
        "https://www.frmanga.com",
        "fr",
        MCCMSConfig(lazyLoadImageAttr = "src"),
    )
