package eu.kanade.tachiyomi.extension.en.eggporncomics

import eu.kanade.tachiyomi.source.model.Filter

internal open class UriPartFilter(
    displayName: String,
    private val vals: Array<Pair<String, String?>>,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    fun toUriPart() = vals[state].second
    fun isNotNull(): Boolean = toUriPart() != null
}

internal class CategoryFilter(name: String, vals: Array<Pair<String, String?>>) : UriPartFilter(name, vals)
internal class ComicsFilter(name: String, vals: Array<Pair<String, String?>>) : UriPartFilter(name, vals)

internal val getCategoryList: Array<Pair<String, String?>> = arrayOf(
    Pair("Any", null),
    Pair("3d comics", "7/3d-comics"),
    Pair("8muses", "18/8muses"),
    Pair("Anime", "1/anime"),
    Pair("Cartoon", "2/cartoon"),
    Pair("Dickgirls & Shemale", "6/dickgirls-shemale"),
    Pair("Furry", "4/furry"),
    Pair("Games comics", "3/games-comics"),
    Pair("Hentai manga", "10/hentai-manga"),
    Pair("Interracial", "14/interracial"),
    Pair("Milf", "11/milf"),
    Pair("Mindcontrol", "15/mindcontrol"),
    Pair("Porn Comix", "16/porn-comix"),
    Pair("Western", "12/western"),
    Pair("Yaoi/Gay", "8/yaoigay"),
    Pair("Yuri and Lesbian", "9/yuri-and-lesbian"),
)

internal val getComicsList: Array<Pair<String, String?>> = arrayOf(
    Pair("Any", null),
    Pair("3d", "85/3d"),
    Pair("Adventure Time", "2950/adventure-time"),
    Pair("Anal", "13/anal"),
    Pair("Ben 10", "641/ben10"),
    Pair("Big boobs", "3025/big-boobs"),
    Pair("Big breasts", "6/big-breasts"),
    Pair("Big cock", "312/big-cock"),
    Pair("Bigass", "604/big-ass-porn-comics-new"),
    Pair("Black cock", "2990/black-cock"),
    Pair("Blowjob", "7/blowjob"),
    Pair("Bondage", "24/bondage"),
    Pair("Breast expansion hentai", "102/breast-expansion-new"),
    Pair("Cumshot", "427/cumshot"),
    Pair("Dark skin", "29/dark-skin"),
    Pair("Dofantasy", "1096/dofantasy"),
    Pair("Double penetration", "87/double-penetration"),
    Pair("Doujin moe", "3028/doujin-moe"),
    Pair("Erotic", "602/erotic"),
    Pair("Fairy tail porn", "3036/fairy-tail"),
    Pair("Fakku", "1712/Fakku-Comics-new"),
    Pair("Fakku comics", "1712/fakku-comics-new"),
    Pair("Family Guy porn", "774/family-guy"),
    Pair("Fansadox", "1129/fansadox-collection"),
    Pair("Feminization", "385/feminization"),
    Pair("Forced", "315/forced"),
    Pair("Full color", "349/full-color"),
    Pair("Furry", "19/furry"),
    Pair("Futanari", "2994/futanari"),
    Pair("Group", "58/group"),
    Pair("Hardcore", "304/hardcore"),
    Pair("Harry Potter porn", "338/harry-potter"),
    Pair("Hentai", "321/hentai"),
    Pair("Incest", "3007/incest"),
    Pair("Incest - Family Therapy Top", "3007/family-therapy-top"),
    Pair("Incognitymous", "545/incognitymous"),
    Pair("Interracical", "608/interracical"),
    Pair("Jab Comix", "1695/JAB-Comics-NEW-2"),
    Pair("Kaos comics", "467/kaos"),
    Pair("Kim Possible porn", "788/kim-possible"),
    Pair("Lesbian", "313/lesbian"),
    Pair("Locofuria", "343/locofuria"),
    Pair("Milf", "48/milf"),
    Pair("Milftoon", "1678/milftoon-comics"),
    Pair("Muscle", "2/muscle"),
    Pair("Nakadashi", "10/nakadashi"),
    Pair("PalComix", "373/palcomix"),
    Pair("Pokemon hentai", "657/pokemon"),
    Pair("Shadbase", "1717/shadbase-comics"),
    Pair("Shemale", "126/shemale"),
    Pair("Slut", "301/slut"),
    Pair("Sparrow hentai", "3035/sparrow-hentai"),
    Pair("Star Wars hentai", "1344/star-wars"),
    Pair("Stockings", "51/stockings"),
    Pair("Superheroine Central", "615/superheroine-central"),
    Pair("The Cummoner", "3034/the-cummoner"),
    Pair("The Rock Cocks", "3031/the-rock-cocks"),
    Pair("ZZZ Comics", "1718/zzz-comics"),
)
