package eu.kanade.tachiyomi.multisrc.paprika

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PaprikaGenerator : ThemeSourceGenerator {

    override val themePkg = "paprika"

    override val themeClass = "Paprika"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("MangaNelos.com", "http://manganelos.com", "en", className = "MangaNelosCom", overrideVersionCode = 1),
        SingleLang("MangaReader.cc", "http://mangareader.cc", "en", className = "MangaReaderCC", overrideVersionCode = 2), // more sites in the future might use MangaReader.cc 's overrides as they did in the past
    )

    override fun createAll() {
        val userDir = System.getProperty("user.dir")!!
        sources.forEach {
            val themeClass = if (it.className == "MangaReaderCC") "PaprikaAlt" else themeClass
            ThemeSourceGenerator.createGradleProject(it, themePkg, themeClass, baseVersionCode, userDir)
        }
        createMultisrcLib(userDir)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PaprikaGenerator().createAll()
        }
    }
}
