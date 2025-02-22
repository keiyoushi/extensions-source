package eu.kanade.tachiyomi.extension.en.ezmanga

import eu.kanade.tachiyomi.multisrc.heancms.HeanCms

class EZmanga : HeanCms(
    "EZmanga",
    "https://ezmanga.org",
    "en",
) {
    // Migrated from Keyoapp to HeanCms
    override val versionId = 3

    override val useNewChapterEndpoint = true
}
