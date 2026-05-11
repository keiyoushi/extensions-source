package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.source.model.Filter

open class UriPartFilter(
    displayName: String,
    // 关键修改：将 values 改名为 vals，避开系统保留字
    private val vals: Array<Pair<String, String>>,
) : Filter.Select<String>(
    displayName,
    vals.map { it.first }.toTypedArray(),
) {
    // 这里的变量名也同步修改
    fun toUriPart(): String = vals[state].second
}

// "None" is intentionally kept as the FIRST entry (index 0) so that
// the `categoryFilter.state > 0` guard in searchMangaRequest works correctly.
// All other entries are sorted at build-time in XAsiatAlbums.getFilterList().
val initialCategories: Map<String, String> = buildMap {
    put("None", "")
    put("China & Taiwan", "albums/categories/china-taiwan")
    put("South Korea", "albums/categories/korea")
    put("JAV & AV Models", "albums/categories/jav")
    put("Gravure Idols", "albums/categories/gravure-idols")
    put("Amateur", "albums/categories/amateur3")
    put("Western Girls", "albums/categories/western-girls")
    put("Southeast Asia", "albums/categories/southeast-asia")
    put("JAV Amateur", "albums/categories/jav-amateur")
    put("Cosplay", "albums/tags/cosplay")
    put("Japanese", "albums/tags/japanese")
    put("Japan", "albums/tags/japan")
    put("Photobook", "albums/tags/photobook")
    put("Friday", "albums/tags/friday")
    put("Korean", "albums/tags/korean")
    put("Friday Digital Photobook", "albums/tags/friday-digital-photobook")
    put("Graphis", "albums/tags/graphis")
    put("Lovepop", "albums/tags/lovepop")
    put("Fantia", "albums/tags/fantia")
    put("Gals", "albums/tags/gals")
    put("Friday Gold", "albums/tags/friday-gold")
    put("Girlz-High", "albums/tags/girlz-high")
    put("Xiuren", "albums/tags/xiuren")
    put("Weekly Playboy", "albums/tags/weekly-playboy")
    put("Leehee Express", "albums/tags/leehee-express")
    put("Flash", "albums/tags/flash")
    put("Young Magazine", "albums/tags/young-magazine")
    put("Bunny", "albums/tags/bunny")
    put("Nude", "albums/tags/nude")
    put("JVID", "albums/tags/jvid")
    put("Maid", "albums/tags/maid")
    put("Artgravia", "albums/tags/artgravia")
    put("Onlyfans", "albums/tags/onlyfans")
    put("Young Jump", "albums/tags/young-jump")
    put("Young Champion", "albums/tags/young-champion")
    put("Big Comic Spirits", "albums/tags/big-comic-spirits")
    put("Uniform", "albums/tags/uniform")
    put("Shonen Magazine", "albums/tags/shonen-magazine")
    put("Xiaoyu", "albums/tags/xiaoyu")
    put("Summertime", "albums/tags/summertime")
    put("Patreon", "albums/tags/patreon")
    put("Swimsuit", "albums/tags/swimsuit")
    put("Tiny Body", "albums/tags/tiny-body")
    put("Yuuhui", "albums/tags/yuuhui")
    put("Yanmaga Web", "albums/tags/yanmaga-web")
    put("Shonen Sunday", "albums/tags/shonen-sunday")
    put("Bejean On Line", "albums/tags/bejean-on-line")
    put("Djawa", "albums/tags/djawa")
    put("Pure Media", "albums/tags/pure-media")
    put("School", "albums/tags/school")
    put("Night", "albums/tags/night")
    put("Espacia Korea", "albums/tags/espacia-korea")
    put("Bikini", "albums/tags/bikini")
    put("Black", "albums/tags/black")
    put("Bluecake", "albums/tags/bluecake")
    put("Teen", "albums/tags/teen")
    put("Loozy", "albums/tags/loozy")
    put("Allgravure", "albums/tags/allgravure")
    put("Girls", "albums/tags/girls")
}
