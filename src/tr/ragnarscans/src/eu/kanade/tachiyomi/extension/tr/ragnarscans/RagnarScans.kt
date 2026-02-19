package eu.kanade.tachiyomi.extension.tr.ragnarscans

import eu.kanade.tachiyomi.multisrc.initmanga.InitManga

class RagnarScans :
    InitManga(
        "Ragnar Scans",
        "https://ragnarscans.com",
        "tr",
        mangaUrlDirectory = "manga",
        popularUrlSlug = "en-cok-takip-edilenler",
        versionId = 2,
    )
