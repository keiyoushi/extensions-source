package eu.kanade.tachiyomi.multisrc.mangathemesia

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

// Formerly WPMangaStream & WPMangaReader -> MangaThemesia
class MangaThemesiaGenerator : ThemeSourceGenerator {

    override val themePkg = "mangathemesia"

    override val themeClass = "MangaThemesia"

    override val baseVersionCode: Int = 28

    override val sources = listOf(
        MultiLang("Miau Scan", "https://miaucomics.org", listOf("es", "pt-BR"), overrideVersionCode = 2),
        SingleLang("Ainz Scans ID", "https://ainzscans.net", "id", overrideVersionCode = 1),
        SingleLang("Alceascan", "https://alceascan.my.id", "id"),
        SingleLang("Animated Glitched Comics", "https://agscomics.com", "en"),
        SingleLang("Animated Glitched Scans", "https://anigliscans.xyz", "en", overrideVersionCode = 1),
        SingleLang("ARESManga", "https://en-aresmanga.com", "ar", pkgName = "iimanga", className = "ARESManga", overrideVersionCode = 2),
        SingleLang("ARESNOV", "https://aresnov.org", "ar"),
        SingleLang("Arven Scans", "https://arvenscans.com", "en"),
        SingleLang("AscalonScans", "https://ascalonscans.com", "en", overrideVersionCode = 1),
        SingleLang("Asura Scans", "https://asuratoon.com", "en", overrideVersionCode = 1),
        SingleLang("Banana-Scan", "https://banana-scan.com", "fr", className = "BananaScan", isNsfw = true),
        SingleLang("Beast Scans", "https://beastscans.net", "ar", overrideVersionCode = 1),
        SingleLang("Berserker Scan", "https://ragnascan.com", "es"),
        SingleLang("BirdManga", "https://birdmanga.com", "en"),
        SingleLang("Boosei", "https://boosei.net", "id", overrideVersionCode = 2),
        SingleLang("Cartel de Manhwas", "https://carteldemanhwas.com", "es", overrideVersionCode = 6),
        SingleLang("Constellar Scans", "https://constellarcomic.com", "en", isNsfw = true, overrideVersionCode = 16),
        SingleLang("Cosmic Scans", "https://cosmic-scans.com", "en", overrideVersionCode = 2),
        SingleLang("CosmicScans.id", "https://cosmicscans.id", "id", overrideVersionCode = 3, className = "CosmicScansID"),
        SingleLang("CulturedWorks", "https://culturedworks.com", "en", isNsfw = true),
        SingleLang("Cypher Scans", "https://cypherscans.xyz", "en"),
        SingleLang("Diskus Scan", "https://diskusscan.com", "pt-BR", overrideVersionCode = 9),
        SingleLang("Dojing.net", "https://dojing.net", "id", isNsfw = true, className = "DojingNet"),
        SingleLang("Elarc Toon", "https://elarctoon.com", "en", className = "ElarcPage", overrideVersionCode = 2),
        SingleLang("EnryuManga", "https://enryumanga.com", "en"),
        SingleLang("Epsilon Scan", "https://epsilonscan.fr", "fr", isNsfw = true),
        SingleLang("Evil production", "https://evil-manga.eu", "cs", isNsfw = true),
        SingleLang("Fairy Manga", "https://fairymanga.com", "en", className = "QueenScans", overrideVersionCode = 1),
        SingleLang("Flame Comics", "https://flamecomics.com", "en"),
        SingleLang("Franxx Mangás", "https://franxxmangas.net", "pt-BR", className = "FranxxMangas", isNsfw = true),
        SingleLang("Freak Scans", "https://freakscans.com", "en"),
        SingleLang("Glory Scans", "https://gloryscans.fr", "fr"),
        SingleLang("Gremory Mangas", "https://gremorymangas.com", "es"),
        SingleLang("Hanuman Scan", "https://hanumanscan.com", "en"),
        SingleLang("Heroxia", "https://heroxia.com", "id", isNsfw = true),
        SingleLang("Hikari Scan", "https://hikariscan.org", "pt-BR", isNsfw = true, overrideVersionCode = 2),
        SingleLang("Imagine Scan", "https://imaginescan.com.br", "pt-BR", isNsfw = true, overrideVersionCode = 1),
        SingleLang("InariManga", "https://inarimanga.com", "es", overrideVersionCode = 7),
        SingleLang("Infernal Void Scans", "https://void-scans.com", "en", overrideVersionCode = 5),
        SingleLang("Kai Scans", "https://kaiscans.org", "en", overrideVersionCode = 1),
        SingleLang("Kanzenin", "https://kanzenin.info", "id", isNsfw = true, overrideVersionCode = 1),
        SingleLang("KataKomik", "https://katakomik.my.id", "id", overrideVersionCode = 1),
        SingleLang("King of Shojo", "https://kingofshojo.com", "ar", overrideVersionCode = 1),
        SingleLang("Kiryuu", "https://kiryuu.id", "id", overrideVersionCode = 6),
        SingleLang("Komik AV", "https://komikav.com", "id", overrideVersionCode = 1),
        SingleLang("Komik Cast", "https://komikcast.lol", "id", overrideVersionCode = 26),
        SingleLang("Komik Lab", "https://komiklab.com", "en", overrideVersionCode = 3),
        SingleLang("Komik Station", "https://komikstation.co", "id", overrideVersionCode = 4),
        SingleLang("KomikIndo.co", "https://komikindo.co", "id", className = "KomikindoCo", overrideVersionCode = 3),
        SingleLang("KomikMama", "https://komik-mama.com", "id", overrideVersionCode = 2),
        SingleLang("KomikManhwa", "https://komikmanhwa.me", "id", isNsfw = true),
        SingleLang("Komiksan", "https://komiksan.link", "id", overrideVersionCode = 2),
        SingleLang("Komiktap", "https://komiktap.me", "id", isNsfw = true, overrideVersionCode = 1),
        SingleLang("Komiku.com", "https://komiku.com", "id", className = "KomikuCom"),
        SingleLang("Kuma Scans (Kuma Translation)", "https://kumascans.com", "en", className = "KumaScans", overrideVersionCode = 1),
        SingleLang("KumaPoi", "https://kumapoi.info", "id", isNsfw = true, overrideVersionCode = 3),
        SingleLang("Legacy Scans", "https://legacy-scans.com", "fr", pkgName = "flamescansfr"),
        SingleLang("Lelmanga", "https://www.lelmanga.com", "fr"),
        SingleLang("LianScans", "https://www.lianscans.my.id", "id", isNsfw = true),
        SingleLang("Luminous Scans", "https://luminousscans.net", "en", overrideVersionCode = 1),
        SingleLang("Lunar Scans", "https://lunarscan.org", "en", isNsfw = true, overrideVersionCode = 1),
        SingleLang("Magus Manga", "https://magusmanga.com", "en", overrideVersionCode = 1),
        SingleLang("Manga Kings", "https://mangakings.com.tr", "tr"),
        SingleLang("Manga Raw.org", "https://mangaraw.org", "ja", className = "MangaRawOrg", overrideVersionCode = 1),
        SingleLang("Mangacim", "https://www.mangacim.com", "tr", overrideVersionCode = 1),
        SingleLang("MangaFlame", "https://mangaflame.org", "ar"),
        SingleLang("MangaKita", "https://mangakita.id", "id", overrideVersionCode = 2),
        SingleLang("Mangakyo", "https://mangakyo.vip", "id", overrideVersionCode = 4),
        SingleLang("MangaNoon", "https://manjanoon.com", "ar", sourceName = "مانجا نون", overrideVersionCode = 1),
        SingleLang("MangaShiina", "https://mangashiina.com", "es"),
        SingleLang("MangaShiro", "https://mangashiro.me", "id"),
        SingleLang("Mangasusu", "https://mangasusuku.xyz", "id", isNsfw = true, overrideVersionCode = 3),
        SingleLang("MangaSwat", "https://goldragon.me", "ar", overrideVersionCode = 15),
        SingleLang("MangaTale", "https://mangatale.co", "id", overrideVersionCode = 2),
        SingleLang("MangaWT", "https://mangawt.com", "tr", overrideVersionCode = 5),
        SingleLang("Mangayaro", "https://www.mangayaro.id", "id", overrideVersionCode = 1),
        SingleLang("MangaYu", "https://mangayu.id", "id"),
        SingleLang("Mangás Chan", "https://mangaschan.net", "pt-BR", className = "MangasChan", overrideVersionCode = 3),
        SingleLang("Mangás Online", "https://mangasonline.cc", "pt-BR", className = "MangasOnline"),
        SingleLang("Manhwa Freak", "https://manhwa-freak.com", "en", overrideVersionCode = 3),
        SingleLang("Manhwa Lover", "https://manhwalover.com", "en", isNsfw = true, overrideVersionCode = 1),
        SingleLang("ManhwaDesu", "https://manhwadesu.one", "id", isNsfw = true, overrideVersionCode = 4),
        SingleLang("ManhwaFreak", "https://manhwafreak.fr", "fr", className = "ManhwaFreakFR"),
        SingleLang("ManhwaIndo", "https://manhwaindo.id", "id", isNsfw = true, overrideVersionCode = 4),
        SingleLang("ManhwaLand.mom", "https://manhwaland.lat", "id", isNsfw = true, className = "ManhwaLandMom", overrideVersionCode = 5),
        SingleLang("ManhwaList", "https://manhwalist.com", "id", overrideVersionCode = 4),
        SingleLang("Manhwax", "https://manhwax.org", "en", isNsfw = true, overrideVersionCode = 1),
        SingleLang("MELOKOMIK", "https://melokomik.xyz", "id"),
        SingleLang("Mihentai", "https://mihentai.com", "all", isNsfw = true, overrideVersionCode = 2),
        SingleLang("Mirai Scans", "https://miraiscans.com", "id"),
        SingleLang("MirrorDesu", "https://mirrordesu.me", "id", isNsfw = true),
        SingleLang("Natsu", "https://natsu.id", "id"),
        SingleLang("Nekomik", "https://nekomik.me", "id", overrideVersionCode = 2),
        SingleLang("NekoScans", "https://nekoscans.com", "es", isNsfw = true),
        SingleLang("Ngomik", "https://ngomik.net", "id", overrideVersionCode = 2),
        SingleLang("NIGHT SCANS", "https://nightscans.net", "en", isNsfw = true, className = "NightScans", overrideVersionCode = 3),
        SingleLang("Noromax", "https://noromax.my.id", "id"),
        SingleLang("Origami Orpheans", "https://origami-orpheans.com", "pt-BR", overrideVersionCode = 10),
        SingleLang("Otsugami", "https://otsugami.id", "id"),
        SingleLang("Ozul Scans", "https://kingofmanga.com", "ar", overrideVersionCode = 2),
        SingleLang("Phantom Scans", "https://phantomscans.com", "en", overrideVersionCode = 1),
        SingleLang("PhenixScans", "https://phenixscans.fr", "fr", overrideVersionCode = 1),
        SingleLang("PotatoManga", "https://potatomanga.xyz", "ar", overrideVersionCode = 1),
        SingleLang("Quantum Scans", "https://readers-point.space", "en"),
        SingleLang("Raiki Scan", "https://raikiscan.com", "es"),
        SingleLang("Raven Scans", "https://ravenscans.com", "en", overrideVersionCode = 1),
        SingleLang("Rawkuma", "https://rawkuma.com", "ja", overrideVersionCode = 1),
        SingleLang("ReadGojo", "https://readgojo.com", "en"),
        SingleLang("Readkomik", "https://readkomik.com", "en", className = "ReadKomik", overrideVersionCode = 1),
        SingleLang("Sekaikomik", "https://sekaikomik.bio", "id", isNsfw = true, overrideVersionCode = 11),
        SingleLang("Sekte Doujin", "https://sektedoujin.cc", "id", isNsfw = true, overrideVersionCode = 5),
        SingleLang("Senpai Ediciones", "http://senpaiediciones.com", "es", overrideVersionCode = 1),
        SingleLang("Shadow Mangas", "https://shadowmangas.com", "es", overrideVersionCode = 1),
        SingleLang("Shea Manga", "https://sheakomik.com", "id", overrideVersionCode = 4),
        SingleLang("Shirakami", "https://shirakami.xyz", "id"),
        SingleLang("Silence Scan", "https://silencescan.com.br", "pt-BR", isNsfw = true, overrideVersionCode = 5),
        SingleLang("Siren Komik", "https://sirenkomik.my.id", "id", className = "MangKomik", overrideVersionCode = 2),
        SingleLang("SkyMangas", "https://skymangas.com", "es", overrideVersionCode = 1),
        SingleLang("Soul Scans", "https://soulscans.my.id", "id", overrideVersionCode = 1),
        SingleLang("SSSScanlator", "https://sssscanlator.com.br", "pt-BR", overrideVersionCode = 2),
        SingleLang("Starlight Scan", "https://starligthscan.com", "pt-BR", isNsfw = true),
        SingleLang("SummerToon", "https://summertoon.com", "tr"),
        SingleLang("Surya Scans", "https://suryatoon.com", "en", overrideVersionCode = 3),
        SingleLang("Sushi-Scan", "https://sushiscan.net", "fr", className = "SushiScan", overrideVersionCode = 10),
        SingleLang("Sushiscan.fr", "https://anime-sama.me", "fr", className = "SushiScanFR", overrideVersionCode = 1),
        SingleLang("Tarot Scans", "https://www.tarotscans.com", "tr"),
        SingleLang("Tecno Scan", "https://tecnoscann.com", "es", isNsfw = true, overrideVersionCode = 6),
        SingleLang("Tempest Fansub", "https://tempestfansub.com", "tr", isNsfw = true),
        SingleLang("Tenshi.id", "https://tenshi.id", "id", className = "TenshiId", pkgName = "masterkomik", overrideVersionCode = 4),
        SingleLang("The Apollo Team", "https://theapollo.team", "en"),
        SingleLang("Thunder Scans", "https://thunderscans.com", "ar"),
        SingleLang("Tres Daos Scan", "https://tresdaos.com", "es"),
        SingleLang("Tsundoku Traduções", "https://tsundoku.com.br", "pt-BR", className = "TsundokuTraducoes", overrideVersionCode = 9),
        SingleLang("TukangKomik", "https://tukangkomik.id", "id", overrideVersionCode = 1),
        SingleLang("TurkToon", "https://turktoon.com", "tr"),
        SingleLang("Uzay Manga", "https://uzaymanga.com", "tr", overrideVersionCode = 6),
        SingleLang("VF Scan", "https://www.vfscan.cc", "fr"),
        SingleLang("Walpurgi Scan", "https://www.walpurgiscan.it", "it", overrideVersionCode = 7, className = "WalpurgisScan"),
        SingleLang("West Manga", "https://westmanga.fun", "id", overrideVersionCode = 3),
        SingleLang("xCaliBR Scans", "https://xcalibrscans.com", "en", overrideVersionCode = 5),
        SingleLang("YumeKomik", "https://yumekomik.com", "id", isNsfw = true, className = "YumeKomik", pkgName = "inazumanga", overrideVersionCode = 6),
        SingleLang("Zahard", "https://zahard.xyz", "en"),
        SingleLang("أريا مانجا", "https://www.areascans.net", "ar", className = "AreaManga"),
        SingleLang("فيكس مانجا", "https://vexmanga.com", "ar", className = "VexManga", overrideVersionCode = 3),
        SingleLang("สดใสเมะ", "https://www.xn--l3c0azab5a2gta.com", "th", isNsfw = true, className = "Sodsaime", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaThemesiaGenerator().createAll()
        }
    }
}
