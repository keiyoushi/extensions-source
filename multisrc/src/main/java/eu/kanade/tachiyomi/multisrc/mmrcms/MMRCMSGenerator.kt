package eu.kanade.tachiyomi.multisrc.mmrcms

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MMRCMSGenerator : ThemeSourceGenerator {

    override val themePkg = "mmrcms"

    override val themeClass = "MMRCMS"

    override val baseVersionCode = 9

    override val sources = listOf(
        SingleLang("Bentoscan", "https://bentoscan.com", "fr"),
        SingleLang("Jpmangas", "https://jpmangas.xyz", "fr", overrideVersionCode = 2),
        SingleLang("Komikid", "https://www.komikid.com", "id"),
        SingleLang("Lelscan-VF", "https://lelscanvf.cc", "fr", className = "LelscanVF", overrideVersionCode = 2),
        SingleLang("Mangadoor", "https://mangadoor.com", "es", overrideVersionCode = 1, isNsfw = true),
        SingleLang("MangaID", "https://mangaid.click", "id", overrideVersionCode = 1),
        SingleLang("Mangas.in", "https://mangas.in", "es", isNsfw = true, className = "MangasIn", overrideVersionCode = 2),
        SingleLang("Manga-Scan", "https://mangascan-fr.com", "fr", className = "MangaScan", overrideVersionCode = 4),
        SingleLang("Onma", "https://onma.top", "ar", sourceName = "مانجا اون لاين"),
        SingleLang("Read Comics Online", "https://readcomicsonline.ru", "en"),
        SingleLang("Scan VF", "https://www.scan-vf.net", "fr", overrideVersionCode = 1),
        SingleLang("Utsukushii", "https://utsukushii-bg.com", "bg", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MMRCMSGenerator().createAll()
        }
    }
}
