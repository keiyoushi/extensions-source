package eu.kanade.tachiyomi.extension.all.galleryepic

import eu.kanade.tachiyomi.source.model.Filter

class ContentFilter : Filter.Select<String>("Content", arrayOf("Cosplay", "Album")) {
    val album get() = state == 1
}

class CosplayerFilter : Filter.Text("Cosplayer (e.g. Atsuki)")

class ModelFilter : Filter.Text("Model (e.g. Budaimao)")

class SeriesFilter(parodies: List<ParodyFilterDto>) :
    Filter.Select<String>(
        "Series",
        arrayOf("All") + parodies.map(ParodyFilterDto::name).toTypedArray(),
    ) {
    private val ids = parodies.map(ParodyFilterDto::id)
    val parodyId: Int? get() = if (state > 0) ids[state - 1] else null
}
