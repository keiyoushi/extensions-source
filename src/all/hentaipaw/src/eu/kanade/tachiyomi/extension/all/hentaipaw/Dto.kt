package eu.kanade.tachiyomi.extension.all.hentaipaw

import kotlinx.serialization.Serializable

@Serializable
class Viewer(val slides: List<Slide>)

@Serializable
class Slide(val src: String)
