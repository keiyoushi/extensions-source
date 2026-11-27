package eu.kanade.tachiyomi.extension.tr.outcatsscans

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source

// The site's admin-ajax archive query returns no series, so the lists are read from its archive pages
@Source
abstract class OutcatsScans : MadaraNoAjax()
