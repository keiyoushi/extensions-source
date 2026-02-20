package eu.kanade.tachiyomi.extension.id.dreamteamsscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class DreamTeamsScans :
    MangaThemesia(
        "DreamTeams Scans",
        "https://dreamteams.space",
        "id",
        "/manga",
        dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")),
    ) {

    override val hasProjectPage = true
}
