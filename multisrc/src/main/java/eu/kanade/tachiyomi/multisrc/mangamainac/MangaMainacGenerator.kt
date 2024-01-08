package eu.kanade.tachiyomi.multisrc.mangamainac

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaMainacGenerator : ThemeSourceGenerator {

    override val themePkg = "mangamainac"

    override val themeClass = "MangaMainac"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Read Boku No Hero Academia Manga Online", "https://w23.readheroacademia.com/", "en"),
        SingleLang("Read One Punch Man Manga Online", "https://w23.readonepunchman.net/", "en", overrideVersionCode = 1),
        SingleLang("Read Solo Leveling", "https://mysololeveling.com/", "en", overrideVersionCode = 1),
        SingleLang("Read Berserk Manga Online", "https://berserkmanga.net/", "en"),
        SingleLang("Read Domestic Girlfriend Manga", "https://domesticgirlfriend.net/", "en"),
        SingleLang("Read Black Clover Manga", "https://w6.blackclovermanga2.com/", "en", overrideVersionCode = 1),
        SingleLang("Read Shingeki no Kyojin Manga", "https://readshingekinokyojin.com/", "en"),
        SingleLang("Read Nanatsu no Taizai Manga", "https://w2.readnanatsutaizai.net/", "en", overrideVersionCode = 1),
        SingleLang("Read Rent a Girlfriend Manga", "https://kanojo-okarishimasu.com/", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaMainacGenerator().createAll()
        }
    }
}
