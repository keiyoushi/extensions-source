package eu.kanade.tachiyomi.multisrc.pizzareader

// import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PizzaReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "pizzareader"

    override val themeClass = "PizzaReader"

    override val baseVersionCode: Int = 2

    override val sources = listOf(
        SingleLang("Phoenix Scans", "https://www.phoenixscans.com", "it", className = "PhoenixScans", overrideVersionCode = 4),
        SingleLang("GTO The Great Site", "https://reader.gtothegreatsite.net", "it", className = "GTO", overrideVersionCode = 3),
        SingleLang("LupiTeam", "https://lupiteam.net", "it", overrideVersionCode = 3),
        SingleLang("TuttoAnimeManga", "https://tuttoanimemanga.net", "it", overrideVersionCode = 4),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PizzaReaderGenerator().createAll()
        }
    }
}
