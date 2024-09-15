package eu.kanade.tachiyomi.extension.es.foyscan

import eu.kanade.tachiyomi.multisrc.mangaesp.MangaEsp

class FoyScan : MangaEsp(
    "Foy Scan",
    "https://foyscan.xyz",
    "es",
    "https://foyscan.xyz",
) {
    // Series path changes from /ver to /serie
    override val versionId = 2

    override val apiPath = "/apiv1"
    override val seriesPath = "/serie"
}
