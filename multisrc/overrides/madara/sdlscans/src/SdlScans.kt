package eu.kanade.tachiyomi.extension.es.sdlscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class SdlScans : Madara("Sdl scans", "https://sdlscans.com", "es", SimpleDateFormat("MMMMM dd, yyyy", Locale("es")))
