package eu.kanade.tachiyomi.extension.fr.reaperscans

import eu.kanade.tachiyomi.multisrc.keyoapp.Keyoapp

class ReaperScans : Keyoapp(
    "Reaper Scans",
    "https://reaper-scans.fr",
    "fr",
) {

    // Migrated from Madara to Keyoapp.
    override val versionId = 4

    override val cdnUrl = "https://3xfsjdlc.is1.buzz/uploads"
}
