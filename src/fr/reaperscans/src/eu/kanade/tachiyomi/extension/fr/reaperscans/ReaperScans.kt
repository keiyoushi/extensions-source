package eu.kanade.tachiyomi.extension.fr.reaperscans
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class ReaperScans : Keyoapp(
    "Reaper Scans",
    "https://reaper-scans.fr",
    "fr",
) {
    override val dateSelector = ".text-xs.w-fit"

    override fun mangaDetailsParse(document: Document): SManga =
        super.mangaDetailsParse(document).apply {
            if (description.isNullOrBlank() == true) {
                description = document.selectFirst("#expand_content > p")?.text()
            }

            // Search for the sibling div of the image with the status icon
            status = document.selectFirst("div:has(> img[src*=status]) + div").parseStatus()

            genre = document.select("div:has(>h1) > div > a").joinToString { it.text() }
        }

    // Migrated from Madara to Keyoapp.
    override val versionId = 4
}
