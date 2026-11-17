package eu.kanade.tachiyomi.extension.all.rule34

import eu.kanade.tachiyomi.source.model.Filter

class CatalogueFilter : Filter.Select<String>("Catalogue", arrayOf("Posts", "Pools"))
