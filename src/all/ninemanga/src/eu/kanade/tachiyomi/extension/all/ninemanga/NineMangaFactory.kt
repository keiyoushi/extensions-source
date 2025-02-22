package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NineMangaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NineMangaEn(),
        NineMangaEs(),
        NineMangaBr(),
        NineMangaRu(),
        NineMangaDe(),
        NineMangaIt(),
        NineMangaFr(),
    )
}

class NineMangaEn : NineManga("NineMangaEn", "https://en.ninemanga.com", "en") {
    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a.bookname").let {
            url = it.attr("abs:href").substringAfter("ninemanga.com")
            title = it.text()
        }
        thumbnail_url = element.select("img").attr("abs:src")
    }
}

class NineMangaEs : NineManga("NineMangaEs", "https://es.ninemanga.com", "es") {
    // ES, FR, RU don't return results for searches with an apostrophe
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return super.searchMangaRequest(page, query.substringBefore("\'"), filters)
    }

    override fun parseStatus(status: String) = when {
        status.contains("En curso") -> SManga.ONGOING
        status.contains("Completado") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    // https://es.ninemanga.com/search/?type=high
    override fun getGenreList() = listOf(
        Genre("4-Koma", "201"),
        Genre("AcciÓN", "213"),
        Genre("AccióN", "69"),
        Genre("Action", "177"),
        Genre("Adventure", "179"),
        Genre("AnimacióN", "229"),
        Genre("ApocalíPtico", "202"),
        Genre("Artes Marciales", "66"),
        Genre("Aventura", "64"),
        Genre("Aventuras", "120"),
        Genre("Boys Love", "228"),
        Genre("Ciberpunk", "225"),
        Genre("Ciencia FiccióN", "93"),
        Genre("Comedia", "75"),
        Genre("Comedy", "178"),
        Genre("Cotidiano", "110"),
        Genre("Crime", "245"),
        Genre("Crimen", "227"),
        Genre("Cyberpunk", "199"),
        Genre("Delincuentes", "125"),
        Genre("Demonios", "126"),
        Genre("Deporte", "76"),
        Genre("Deportes", "111"),
        Genre("Doujinshi", "216"),
        Genre("Drama", "79"),
        Genre("Escolar", "81"),
        Genre("Extranjero", "238"),
        Genre("Familia", "237"),
        Genre("Fantacia", "100"),
        Genre("Fantasy", "180"),
        Genre("FantasÍA", "214"),
        Genre("FantasíA", "70"),
        Genre("GL (Girls Love)", "222"),
        Genre("Gender Bender", "175"),
        Genre("Girls Love", "226"),
        Genre("Gore", "108"),
        Genre("Guerra", "234"),
        Genre("GéNero Bender", "230"),
        Genre("HaréN", "82"),
        Genre("Hentai", "83"),
        Genre("Historia", "233"),
        Genre("Historical", "190"),
        Genre("HistóRico", "95"),
        Genre("Horror", "99"),
        Genre("Isekai", "240"),
        Genre("Josei", "112"),
        Genre("Karate", "113"),
        Genre("Maduro", "72"),
        Genre("Mafia", "90"),
        Genre("Magia", "172"),
        Genre("Makoto", "102"),
        Genre("Mangasutra", "103"),
        Genre("Manhwa", "94"),
        Genre("Manwha", "114"),
        Genre("Martial Arts", "181"),
        Genre("Martial", "189"),
        Genre("Mecha", "115"),
        Genre("Militar", "205"),
        Genre("Misterio", "88"),
        Genre("Music", "241"),
        Genre("Musical", "197"),
        Genre("Mystery", "187"),
        Genre("MúSica", "121"),
        Genre("NiñOs", "235"),
        Genre("None", "71"),
        Genre("Oeste", "239"),
        Genre("One Shot", "184"),
        Genre("One-Shot", "221"),
        Genre("Oneshot", "195"),
        Genre("OrgíA", "91"),
        Genre("Parodia", "198"),
        Genre("Policiaco", "236"),
        Genre("Policial", "208"),
        Genre("PolicíAca", "220"),
        Genre("Porno", "109"),
        Genre("PsicolóGica", "219"),
        Genre("PsicolóGico", "96"),
        Genre("Psychological", "192"),
        Genre("Realidad Virtual", "196"),
        Genre("Realidad", "231"),
        Genre("Recuentos De La Vida", "169"),
        Genre("ReencarnacióN", "207"),
        Genre("Romance", "67"),
        Genre("RomáNtica", "98"),
        Genre("RomáNtico", "89"),
        Genre("Samurai", "210"),
        Genre("School Life", "176"),
        Genre("Sci-Fi", "123"),
        Genre("Seinen", "73"),
        Genre("Shojo Ai", "186"),
        Genre("Shojo", "80"),
        Genre("Shojo-Ai (Yuri Soft)", "218"),
        Genre("Shonen Ai", "128"),
        Genre("Shonen", "77"),
        Genre("Shonen-Ai (Yaoi Soft)", "217"),
        Genre("Shonen-Ai", "174"),
        Genre("Shota", "224"),
        Genre("Shoujo Ai", "194"),
        Genre("Shoujo", "85"),
        Genre("Shoujo-Ai", "173"),
        Genre("Shounen Ai", "185"),
        Genre("Shounen", "68"),
        Genre("Shounen-Ai", "118"),
        Genre("Slice Of Life", "182"),
        Genre("Sobrenatural", "74"),
        Genre("Sports", "188"),
        Genre("Super Natural", "124"),
        Genre("Super Poderes", "206"),
        Genre("Superhero", "246"),
        Genre("Superheroes", "116"),
        Genre("Supernatural", "119"),
        Genre("Superpoderes", "215"),
        Genre("Supervivencia", "203"),
        Genre("Suspense", "171"),
        Genre("Telenovela", "242"),
        Genre("Terror PsicolóGico", "107"),
        Genre("Terror", "106"),
        Genre("Thiller", "204"),
        Genre("Thriller", "97"),
        Genre("Tragedia", "87"),
        Genre("Tragedy", "191"),
        Genre("Vampiros", "209"),
        Genre("Ver En Lectormanga", "243"),
        Genre("Vida Cotidiana", "84"),
        Genre("Vida Escolar", "170"),
        Genre("Vida Escolar.", "122"),
        Genre("Webcomic", "92"),
        Genre("Webtoon", "200"),
        Genre("Wuxia", "244"),
        Genre("Yonkoma", "232"),
    )
}

class NineMangaBr : NineManga("NineMangaBr", "https://br.ninemanga.com", "pt-BR") {

    // Hardcode the id because the language wasn't specific.
    override val id: Long = 7162569729467394726

    override fun parseStatus(status: String) = when {
        status.contains("Em tradução") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    // https://br.ninemanga.com/search/?type=high
    override fun getGenreList() = listOf(
        Genre("4koma", "107"),
        Genre("Adulto (18+)", "123"),
        Genre("Adulto (YAOI)", "122"),
        Genre("Artes Marciais", "83"),
        Genre("Aventura", "72"),
        Genre("AçãO", "71"),
        Genre("Bara", "126"),
        Genre("Carros", "118"),
        Genre("Colegial", "63"),
        Genre("ComéDia", "64"),
        Genre("Criancas", "114"),
        Genre("Culinaria", "116"),
        Genre("Dementia", "119"),
        Genre("Demonios", "109"),
        Genre("Doujinshi", "124"),
        Genre("Drama", "74"),
        Genre("Escolar", "103"),
        Genre("Espaco", "117"),
        Genre("Esporte", "87"),
        Genre("Esportes", "106"),
        Genre("Fantasia", "65"),
        Genre("FicçãO", "99"),
        Genre("Gender Bender", "73"),
        Genre("HistóRico", "77"),
        Genre("Horror", "80"),
        Genre("Isekai", "121"),
        Genre("Jogo", "102"),
        Genre("Josei", "89"),
        Genre("Maduro", "105"),
        Genre("Magia", "96"),
        Genre("Manhua", "125"),
        Genre("Manhwa", "129"),
        Genre("Mecha", "94"),
        Genre("Medicina", "131"),
        Genre("Militar", "110"),
        Genre("MistéRio", "78"),
        Genre("Musical", "92"),
        Genre("Nonsense", "120"),
        Genre("Novel", "130"),
        Genre("OneShot", "69"),
        Genre("Parodia", "108"),
        Genre("Policial", "101"),
        Genre("PsicolóGico", "79"),
        Genre("Romance", "66"),
        Genre("Samurai", "111"),
        Genre("Sci-Fi", "67"),
        Genre("Seinen", "82"),
        Genre("Shoujo Ai", "100"),
        Genre("Shoujo", "70"),
        Genre("Shoujo-Ai", "86"),
        Genre("Shounen Ai", "95"),
        Genre("Shounen", "68"),
        Genre("Slice Of Life", "75"),
        Genre("Sobrenatural", "76"),
        Genre("Super Poderes", "113"),
        Genre("Suspense", "127"),
        Genre("Terror", "91"),
        Genre("Teste 1", "97"),
        Genre("Thriller", "115"),
        Genre("TragéDia", "81"),
        Genre("Vampiros", "112"),
        Genre("Webtoon", "128"),
        Genre("Xuanhuan", "104"),
        Genre("Yaoi (Omegaverse)", "132"),
    )
}

class NineMangaRu : NineManga("NineMangaRu", "https://ru.ninemanga.com", "ru") {
    // ES, FR, RU don't return results for searches with an apostrophe
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return super.searchMangaRequest(page, query.substringBefore("\'"), filters)
    }

    override fun parseStatus(status: String) = when {
        // No Ongoing status
        status.contains("завершенный") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    // https://ru.ninemanga.com/search/?type=high
    override fun getGenreList() = listOf(
        Genre("Бдсм", "95"),
        Genre("арт", "90"),
        Genre("боевик", "53"),
        Genre("боевые искусства", "58"),
        Genre("вампиры", "85"),
        Genre("гарем", "73"),
        Genre("гендерная интрига", "81"),
        Genre("героическое фэнтези", "68"),
        Genre("детектив", "72"),
        Genre("дзёсэй", "64"),
        Genre("додзинси", "62"),
        Genre("драма", "51"),
        Genre("игра", "76"),
        Genre("история", "75"),
        Genre("киберпанк", "91"),
        Genre("кодомо", "89"),
        Genre("комедия", "57"),
        Genre("махо-сёдзё", "88"),
        Genre("меха", "84"),
        Genre("мистика", "71"),
        Genre("научная фантастика", "79"),
        Genre("омегаверс", "94"),
        Genre("повседневность", "65"),
        Genre("постапокалиптика", "87"),
        Genre("приключения", "59"),
        Genre("психология", "54"),
        Genre("романтика", "61"),
        Genre("самурайский боевик", "82"),
        Genre("сверхъестественное", "55"),
        Genre("спорт", "69"),
        Genre("сэйнэн", "74"),
        Genre("сёдзё", "67"),
        Genre("сёдзё-ай", "78"),
        Genre("сёнэн", "52"),
        Genre("сёнэн-ай", "63"),
        Genre("трагедия", "70"),
        Genre("триллер", "83"),
        Genre("ужасы", "86"),
        Genre("фантастика", "77"),
        Genre("фэнтези", "56"),
        Genre("школа", "66"),
        Genre("эротика", "93"),
        Genre("этти", "60"),
        Genre("юри", "80"),
        Genre("яой", "92"),
    )
}

class NineMangaDe : NineManga("NineMangaDe", "https://de.ninemanga.com", "de") {
    override fun parseStatus(status: String) = when {
        status.contains("Laufende") -> SManga.ONGOING
        status.contains("Abgeschlossen") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    // https://de.ninemanga.com/search/?type=high
    override fun getGenreList() = listOf(
        Genre("4-Koma", "104"),
        Genre("Abenteuer", "63"),
        Genre("Action", "64"),
        Genre("Alltagsdrama", "82"),
        Genre("Boys Love", "106"),
        Genre("Doujinshi", "97"),
        Genre("Drama", "65"),
        Genre("DäMonen", "76"),
        Genre("Erotik", "88"),
        Genre("Fantasy", "66"),
        Genre("Geister", "108"),
        Genre("Gender Bender", "91"),
        Genre("Girls Love", "99"),
        Genre("Historisch", "84"),
        Genre("Horror", "72"),
        Genre("Isekai", "109"),
        Genre("Josei", "95"),
        Genre("Kampfsport", "81"),
        Genre("Kartenspiel", "78"),
        Genre("Kinder", "101"),
        Genre("Kochen", "107"),
        Genre("KomöDie", "67"),
        Genre("Krimi", "105"),
        Genre("Magie", "68"),
        Genre("Mecha", "89"),
        Genre("MilitäR", "90"),
        Genre("Monster", "100"),
        Genre("Musik", "83"),
        Genre("Mystery", "69"),
        Genre("Psychodrama", "103"),
        Genre("Romanze", "74"),
        Genre("Schule", "70"),
        Genre("Sci-Fi", "86"),
        Genre("Seinen", "96"),
        Genre("Shoujo", "85"),
        Genre("Shounen", "75"),
        Genre("Spiel", "92"),
        Genre("Sport", "87"),
        Genre("Super KräFte", "80"),
        Genre("SuperkräFte", "102"),
        Genre("Thriller", "94"),
        Genre("Vampire", "71"),
        Genre("Videospiel", "77"),
    )
}

class NineMangaIt : NineManga("NineMangaIt", "https://it.ninemanga.com", "it") {
    override fun parseStatus(status: String) = when {
        status.contains("In corso") -> SManga.ONGOING
        status.contains("Completato") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    // https://it.ninemanga.com/search/?type=high
    override fun getGenreList() = listOf(
        Genre("Action", "98"),
        Genre("Adventure", "108"),
        Genre("Avventura", "63"),
        Genre("Azione", "65"),
        Genre("Bara", "88"),
        Genre("Cartoon", "120"),
        Genre("Comedy", "101"),
        Genre("Commedia", "71"),
        Genre("Demenziale", "79"),
        Genre("Doujinshi", "114"),
        Genre("Dounshinji", "92"),
        Genre("Drama", "82"),
        Genre("Fantasy", "74"),
        Genre("Gender Bender", "109"),
        Genre("Green", "119"),
        Genre("Hentai", "90"),
        Genre("Historical", "107"),
        Genre("Horror", "80"),
        Genre("Josei", "95"),
        Genre("Magico", "91"),
        Genre("Manga", "121"),
        Genre("Martial Arts", "99"),
        Genre("Maturo", "115"),
        Genre("Mecha", "68"),
        Genre("Misteri", "87"),
        Genre("Musica", "96"),
        Genre("Mystery", "105"),
        Genre("Psicologico", "83"),
        Genre("Psychological", "97"),
        Genre("Raccolta", "93"),
        Genre("Realistico", "118"),
        Genre("Romance", "104"),
        Genre("Romantico", "75"),
        Genre("School Life", "103"),
        Genre("Sci-Fi", "66"),
        Genre("Scolastico", "64"),
        Genre("Seinen", "67"),
        Genre("Sentimentale", "72"),
        Genre("Shota", "89"),
        Genre("Shoujo", "73"),
        Genre("Shounen", "69"),
        Genre("Slice Of Life", "102"),
        Genre("Sovrannaturale", "78"),
        Genre("Splatter", "81"),
        Genre("Sportivo", "85"),
        Genre("Sports", "110"),
        Genre("Storico", "84"),
        Genre("Supereroistico", "117"),
        Genre("Supernatural", "100"),
        Genre("Tragedia", "116"),
        Genre("Tragedy", "112"),
        Genre("Vita Quotidiana", "77"),
    )
}

class NineMangaFr : NineManga("NineMangaFr", "https://fr.ninemanga.com", "fr") {
    // ES, FR, RU don't return results for searches with an apostrophe
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return super.searchMangaRequest(page, query.substringBefore("\'"), filters)
    }

    override fun parseStatus(status: String) = when {
        status.contains("En cours") -> SManga.ONGOING
        status.contains("Complété") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun parseChapterDate(date: String) = parseChapterDateByLang(date)

    // https://fr.ninemanga.com/search/?type=high
    override fun getGenreList() = listOf(
        Genre("AcadéMie", "175"),
        Genre("Action", "5"),
        Genre("Adolescence", "205"),
        Genre("Adulte", "52"),
        Genre("Adventure", "27"),
        Genre("Agriculture", "121"),
        Genre("Alice Aux Pays Des Merveilles", "253"),
        Genre("Aliens", "109"),
        Genre("Alpinisme", "243"),
        Genre("Ambition", "282"),
        Genre("Amitié", "13"),
        Genre("Amour", "146"),
        Genre("Anges", "98"),
        Genre("Angleterre", "283"),
        Genre("Animaux", "120"),
        Genre("Apprentissage", "89"),
        Genre("Argent", "263"),
        Genre("Arnaque", "259"),
        Genre("Arts Martiaux", "24"),
        Genre("Assassinat", "84"),
        Genre("Astronaute", "186"),
        Genre("Autre Monde", "92"),
        Genre("Aventure", "11"),
        Genre("Aviation", "206"),
        Genre("Bandit", "71"),
        Genre("Baseball", "169"),
        Genre("Basket", "287"),
        Genre("Basketball", "288"),
        Genre("Baston", "157"),
        Genre("Bataille Navale", "203"),
        Genre("Bateau", "266"),
        Genre("Biographique", "285"),
        Genre("Boxe", "103"),
        Genre("Bug", "215"),
        Genre("Cafard", "216"),
        Genre("Campagne", "172"),
        Genre("Camping", "291"),
        Genre("Cartes", "268"),
        Genre("Chantage", "57"),
        Genre("Chasseur", "23"),
        Genre("Chevalier", "72"),
        Genre("Clonage", "207"),
        Genre("Club", "123"),
        Genre("Coach", "195"),
        Genre("Cobaye", "217"),
        Genre("CollèGe", "208"),
        Genre("Combats", "14"),
        Genre("Comedy", "25"),
        Genre("CompéTition", "127"),
        Genre("ComÉDie", "81"),
        Genre("ComéDie", "6"),
        Genre("Conte", "254"),
        Genre("Cosmos", "270"),
        Genre("Course", "245"),
        Genre("Crime", "66"),
        Genre("Crossdressing", "53"),
        Genre("CréAture", "182"),
        Genre("Cuisine", "34"),
        Genre("Cyberpunk", "264"),
        Genre("Cyborgs", "119"),
        Genre("Death Game", "279"),
        Genre("Destin", "269"),
        Genre("Dette", "260"),
        Genre("Dimension", "134"),
        Genre("Don", "185"),
        Genre("Doujinshi", "278"),
        Genre("Dragons", "197"),
        Genre("Drama", "35"),
        Genre("Drame", "2"),
        Genre("Drift", "246"),
        Genre("Dystopie", "112"),
        Genre("DéLinquant", "222"),
        Genre("DéLinquants", "148"),
        Genre("DéMons", "18"),
        Genre("DéTective", "122"),
        Genre("Ecole", "49"),
        Genre("Empire", "223"),
        Genre("Enfance", "231"),
        Genre("Enfer", "237"),
        Genre("EnquêTe", "228"),
        Genre("Entomologie", "218"),
        Genre("Erotique", "158"),
        Genre("Escalade", "271"),
        Genre("Espace", "135"),
        Genre("Espionnage", "199"),
        Genre("Esprit", "22"),
        Genre("Extra-Terrestres", "136"),
        Genre("Famille", "54"),
        Genre("Fantastique", "1"),
        Genre("Fantasy", "28"),
        Genre("FantôMes", "20"),
        Genre("Feu", "255"),
        Genre("Filles Et Pistolets", "152"),
        Genre("Flamme", "256"),
        Genre("Folklore", "78"),
        Genre("Football", "239"),
        Genre("Fruit", "7"),
        Genre("FrèRe", "187"),
        Genre("Fuite", "214"),
        Genre("Furyo", "209"),
        Genre("Game", "129"),
        Genre("Garde Du Corps", "167"),
        Genre("Gastronomie", "97"),
        Genre("Gender Bender", "51"),
        Genre("Genderswap", "171"),
        Genre("Glace", "257"),
        Genre("Gore", "105"),
        Genre("Guerre", "15"),
        Genre("Guerrier", "225"),
        Genre("GéNie", "229"),
        Genre("GéNéTique", "219"),
        Genre("Handicap", "162"),
        Genre("HarcèLement", "161"),
        Genre("Harem Inversé", "274"),
        Genre("Heroic-Fantasy", "140"),
        Genre("Histoire", "154"),
        Genre("Histoires Courtes", "160"),
        Genre("Historical", "41"),
        Genre("Historique", "76"),
        Genre("Homosexualité", "267"),
        Genre("Horreur", "19"),
        Genre("Horror", "63"),
        Genre("Humour", "79"),
        Genre("Idols", "191"),
        Genre("Immortalité", "132"),
        Genre("Insecte", "220"),
        Genre("Isekai", "36"),
        Genre("Jeu", "70"),
        Genre("Jeunesse", "232"),
        Genre("Jeux VidéO", "147"),
        Genre("Josei", "94"),
        Genre("Justicier", "176"),
        Genre("Kaiju", "289"),
        Genre("LittéRature", "196"),
        Genre("Loli", "244"),
        Genre("Love Hotel", "58"),
        Genre("Lune", "188"),
        Genre("LycéE", "126"),
        Genre("Mafia", "142"),
        Genre("Magical Girl", "99"),
        Genre("Magical Girls", "286"),
        Genre("Magie", "8"),
        Genre("MaléDiction", "193"),
        Genre("Maritime", "202"),
        Genre("Mars", "221"),
        Genre("Massacre", "258"),
        Genre("Matchs", "125"),
        Genre("Mecha", "68"),
        Genre("Mechas", "153"),
        Genre("Medical", "65"),
        Genre("Mentor", "177"),
        Genre("Militaire", "115"),
        Genre("Mmo", "226"),
        Genre("Monster Girls", "252"),
        Genre("Monstres", "77"),
        Genre("Montagne", "272"),
        Genre("Mort", "133"),
        Genre("Moto", "210"),
        Genre("Moyen ÂGe", "106"),
        Genre("Musique", "151"),
        Genre("Mystery", "40"),
        Genre("MystÈRe", "85"),
        Genre("MystèRe", "3"),
        Genre("MéDecine", "137"),
        Genre("MéDiéVal", "139"),
        Genre("Nasa", "189"),
        Genre("Nature", "227"),
        Genre("Navire", "265"),
        Genre("Nekketsu", "178"),
        Genre("Ninjas", "59"),
        Genre("Nostalgie", "242"),
        Genre("Nourriture", "33"),
        Genre("One Shot", "173"),
        Genre("Organisations SecrèTes", "138"),
        Genre("Orphelin", "212"),
        Genre("Otage", "280"),
        Genre("Otaku", "190"),
        Genre("Paranormal", "131"),
        Genre("Parodie", "96"),
        Genre("Philosophical", "64"),
        Genre("Philosophique", "235"),
        Genre("Pirates", "9"),
        Genre("Plage", "275"),
        Genre("PlongéE", "276"),
        Genre("Police", "236"),
        Genre("Policier", "150"),
        Genre("Politique", "91"),
        Genre("Post-Apocalypse", "234"),
        Genre("Post-Apocalyptique", "113"),
        Genre("Pouvoirs Psychiques", "130"),
        Genre("Pouvoirs", "10"),
        Genre("Princesse", "166"),
        Genre("Prison", "156"),
        Genre("Professeur", "181"),
        Genre("Promenade", "273"),
        Genre("Prostitution", "261"),
        Genre("Psychological", "61"),
        Genre("Psychologie", "74"),
        Genre("Psychologique", "42"),
        Genre("Quotidien", "93"),
        Genre("Racing", "247"),
        Genre("Religion", "201"),
        Genre("Robots", "233"),
        Genre("Roi", "12"),
        Genre("Romance", "26"),
        Genre("Rpg", "141"),
        Genre("RéIncarnation", "107"),
        Genre("RêVes", "149"),
        Genre("Sabre", "144"),
        Genre("Sadique", "55"),
        Genre("Samourai", "145"),
        Genre("Samurai", "155"),
        Genre("School Life", "43"),
        Genre("Sci-Fi", "44"),
        Genre("Science-Fiction", "31"),
        Genre("Scientifique", "174"),
        Genre("Scolaire", "163"),
        Genre("Secrets", "184"),
        Genre("Seinen", "88"),
        Genre("Sherlock Holmes", "284"),
        Genre("Shinigami", "21"),
        Genre("Shogi", "165"),
        Genre("Shojo Ai", "87"),
        Genre("Shojo", "101"),
        Genre("Shonen Ai", "240"),
        Genre("Shonen", "80"),
        Genre("Shoujo Ai", "45"),
        Genre("Shounen Ai", "39"),
        Genre("Slice Of Life", "29"),
        Genre("Social", "69"),
        Genre("SociéTé", "118"),
        Genre("Sonyun-Manhwa", "170"),
        Genre("Sport", "102"),
        Genre("Sports MéCaniques", "251"),
        Genre("Sports", "67"),
        Genre("Steampunk", "116"),
        Genre("Suicide", "238"),
        Genre("Super Pouvoirs", "16"),
        Genre("Super-HéRos", "180"),
        Genre("Super-Vilains", "179"),
        Genre("Superhero", "62"),
        Genre("Surnaturel", "4"),
        Genre("Survival Game", "117"),
        Genre("Survival", "290"),
        Genre("Survivre", "213"),
        Genre("Suspense", "75"),
        Genre("Talent", "230"),
        Genre("Tennis", "183"),
        Genre("Thriller", "128"),
        Genre("Titans", "114"),
        Genre("Tournois", "30"),
        Genre("Traditions", "204"),
        Genre("Tragedy", "37"),
        Genre("Tragique", "111"),
        Genre("TragéDie", "73"),
        Genre("Tranche De Vie", "48"),
        Genre("Transidentité", "143"),
        Genre("Travail", "198"),
        Genre("Travestissement", "192"),
        Genre("Triangle Amoureux", "168"),
        Genre("Tuning", "248"),
        Genre("Usurier", "262"),
        Genre("Vampires", "100"),
        Genre("Vengeance", "83"),
        Genre("Video Games", "281"),
        Genre("Vie Scolaire", "86"),
        Genre("Violence", "194"),
        Genre("Virtuel", "200"),
        Genre("Vitesse", "249"),
        Genre("Voiture", "250"),
        Genre("Volley-Ball", "124"),
        Genre("Voyage Dans Le Temps", "104"),
        Genre("Voyage Temporel", "108"),
        Genre("Voyage", "17"),
        Genre("Voyou", "211"),
        Genre("WTF", "110"),
        Genre("Webtoon", "32"),
        Genre("Wuxia", "46"),
        Genre("Yakuza", "95"),
        Genre("Yokai", "241"),
        Genre("Yonkoma", "159"),
        Genre("Zombies", "277"),
        Genre("éChec", "164"),
        Genre("éPéE", "224"),
    )
}

fun parseChapterDateByLang(date: String): Long {
    val dateWords = date.split(" ")

    if (dateWords.size == 3) {
        if (dateWords[1].contains(",")) {
            return try {
                SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date)?.time ?: 0L
            } catch (e: ParseException) {
                0L
            }
        } else {
            val timeAgo = Integer.parseInt(dateWords[0])
            return Calendar.getInstance().apply {
                when (dateWords[1]) {
                    "minutos" -> Calendar.MINUTE // ES
                    "horas" -> Calendar.HOUR

                    // "minutos" -> Calendar.MINUTE // BR
                    "hora" -> Calendar.HOUR

                    "минут" -> Calendar.MINUTE // RU
                    "часа" -> Calendar.HOUR

                    "Stunden" -> Calendar.HOUR // DE

                    "minuti" -> Calendar.MINUTE // IT
                    "ore" -> Calendar.HOUR

                    "minutes" -> Calendar.MINUTE // FR
                    "heures" -> Calendar.HOUR
                    else -> null
                }?.let {
                    add(it, -timeAgo)
                }
            }.timeInMillis
        }
    }
    return 0L
}
