package eu.kanade.tachiyomi.extension.ar.galaxymanga

import eu.kanade.tachiyomi.multisrc.flixscans.FlixScans

class GalaxyManga : FlixScans(
    "جالاكسي مانجا",
    "https://flixscans.com",
    "ar",
    "https://ar.flixscans.site/api/v1",
) {
    override val versionId = 2
}
