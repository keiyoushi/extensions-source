package eu.kanade.tachiyomi.extension.all.batotov4

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SelectFilterOption(val name: String, val value: String)
class CheckboxFilterOption(val value: String, name: String, default: Boolean = false) : Filter.CheckBox(name, default)
class TriStateFilterOption(val value: String, name: String, default: Int = 0) : Filter.TriState(name, default)

abstract class SelectFilter(
    name: String,
    private val options: List<SelectFilterOption>,
    default: Int = 0,
) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
    val selected: String
        get() = options[state].value
}

abstract class TextFilter(name: String) : Filter.Text(name)

abstract class CheckboxGroupFilter(
    name: String,
    options: List<CheckboxFilterOption>,
) : Filter.Group<CheckboxFilterOption>(name, options) {
    val selected: List<String>
        get() = state.filter { it.state }.map { it.value }
}

abstract class TriStateGroupFilter(
    name: String,
    options: List<TriStateFilterOption>,
) : Filter.Group<TriStateFilterOption>(name, options) {
    val included: List<String>
        get() = state.filter { it.isIncluded() }.map { it.value }

    val excluded: List<String>
        get() = state.filter { it.isExcluded() }.map { it.value }
}

class LetterFilter : SelectFilter("Letter matching mode (Slow)", options) {
    companion object {
        private val options = listOf(
            SelectFilterOption("Disabled", "disabled"),
            SelectFilterOption("Enabled", "enabled"),
        )
    }
}

class SortFilter(
    defaultIndex: Int = LATEST_INDEX,
) : Filter.Sort("Order by", sortNames(), Selection(defaultIndex, false)) {
    val selected: String
        get() = options[state!!.index].value

    companion object {
        public const val POPULAR_INDEX = 0 // field_score
        public const val LATEST_INDEX = 5 // field_upload

        private val options = listOf(
            SelectFilterOption("Rating Score", "field_score"),
            SelectFilterOption("Most Follows", "field_follow"),
            SelectFilterOption("Most Reviews", "field_review"),
            SelectFilterOption("Most Comments", "field_comment"),
            SelectFilterOption("Most Chapters", "field_chapter"),
            SelectFilterOption("Latest Upload", "field_upload"),
            SelectFilterOption("Recently Created", "field_public"),
            SelectFilterOption("Name A-Z", "field_name"),
            SelectFilterOption("Most Views (Total)", "views_d000"),
            SelectFilterOption("Most Views (360 days)", "views_d360"),
            SelectFilterOption("Most Views (180 days)", "views_d180"),
            SelectFilterOption("Most Views (90 days)", "views_d090"),
            SelectFilterOption("Most Views (30 days)", "views_d030"),
            SelectFilterOption("Most Views (7 days)", "views_d007"),
            SelectFilterOption("Most Views (24 hours)", "views_h024"),
            SelectFilterOption("Most Views (12 hours)", "views_h012"),
            SelectFilterOption("Most Views (6 hours)", "views_h006"),
            SelectFilterOption("Most Views (1 hour)", "views_h001"),
            SelectFilterOption("User Status (Plan to Read)", "status_wish"),
            SelectFilterOption("User Status (Reading)", "status_doing"),
            SelectFilterOption("User Status (Completed)", "status_completed"),
            SelectFilterOption("User Status (On Hold)", "status_on_hold"),
            SelectFilterOption("User Status (Dropped)", "status_dropped"),
            SelectFilterOption("User Status (Re-reading)", "status_repeat"),
            SelectFilterOption("Emotions (Awesome)", "emotion_upvote"),
            SelectFilterOption("Emotions (Funny)", "emotion_funny"),
            SelectFilterOption("Emotions (Love)", "emotion_love"),
            SelectFilterOption("Emotions (Scared)", "emotion_surprise"),
            SelectFilterOption("Emotions (Angry)", "emotion_angry"),
            SelectFilterOption("Emotions (Sad)", "emotion_sad"),
        )

        private fun sortNames() = options.map { it.name }.toTypedArray()

        val POPULAR = FilterList(SortFilter(POPULAR_INDEX))
        val LATEST = FilterList(SortFilter(LATEST_INDEX))
    }
}

class PersonalListFilter : SelectFilter("Personal list", options) {
    companion object {
        private val options = listOf(
            SelectFilterOption("None", ""),
            SelectFilterOption("My History", "history"),
            SelectFilterOption("My Updates", "updates"),
        )
    }
}

class UtilsFilter : SelectFilter("Utils comic list", options) {
    companion object {
        private val options = listOf(
            SelectFilterOption("None", ""),
            SelectFilterOption("Comics: I Uploaded", "editor=upload"),
            SelectFilterOption("Comics: I Created", "editor=create"),
            SelectFilterOption("Comics: I Modified", "editor=modify"),
            SelectFilterOption("Comics: Whitelisted me", "editor=whitelist"),
            SelectFilterOption("Comics: Blacklisted me", "editor=blacklist"),
            SelectFilterOption("Comics: Any I participated", ""),
            SelectFilterOption("Comics: Draft Status", "dbStatus=draft"),
            SelectFilterOption("Comics: Hidden Status", "dbStatus=hidden"),
            // Below are for "I uploaded + Ongoing + Normal + Not updated in X days" shortcuts
            SelectFilterOption("Uploaded+Ongoing: Not updated in 7+ days", "editor=upload&siteStatus=ongoing&dbStatus=normal&mod_lock=n&mod_hide=n&notUpdatedDays=7"),
            SelectFilterOption("Uploaded+Ongoing: Not updated in 30+ days", "editor=upload&siteStatus=ongoing&dbStatus=normal&mod_lock=n&mod_hide=n&notUpdatedDays=30"),
            SelectFilterOption("Uploaded+Ongoing: Not updated in 60+ days", "editor=upload&siteStatus=ongoing&dbStatus=normal&mod_lock=n&mod_hide=n&notUpdatedDays=60"),
            SelectFilterOption("Uploaded+Ongoing: Not updated in 90+ days", "editor=upload&siteStatus=ongoing&dbStatus=normal&mod_lock=n&mod_hide=n&notUpdatedDays=90"),
            SelectFilterOption("Uploaded+Ongoing: Not updated in 180+ days", "editor=upload&siteStatus=ongoing&dbStatus=normal&mod_lock=n&mod_hide=n&notUpdatedDays=180"),
            SelectFilterOption("Uploaded+Ongoing: Not updated in 360+ days", "editor=upload&siteStatus=ongoing&dbStatus=normal&mod_lock=n&mod_hide=n&notUpdatedDays=360"),
            SelectFilterOption("Uploaded+Ongoing: Not updated in 1000+ days", "editor=upload&siteStatus=ongoing&dbStatus=normal&mod_lock=n&mod_hide=n&notUpdatedDays=1000"),
        )
    }
}

class OriginalStatusFilter : SelectFilter("Original Work Status", options) {
    companion object {
        private val options = listOf(
            SelectFilterOption("All", ""),
            SelectFilterOption("Pending", "pending"),
            SelectFilterOption("Ongoing", "ongoing"),
            SelectFilterOption("Completed", "completed"),
            SelectFilterOption("Hiatus", "hiatus"),
            SelectFilterOption("Cancelled", "cancelled"),
        )
    }
}

class UploadStatusFilter : SelectFilter("Bato Upload Status", options) {
    companion object {
        private val options = listOf(
            SelectFilterOption("All", ""),
            SelectFilterOption("Pending", "pending"),
            SelectFilterOption("Ongoing", "ongoing"),
            SelectFilterOption("Completed", "completed"),
            SelectFilterOption("Hiatus", "hiatus"),
            SelectFilterOption("Cancelled", "cancelled"),
        )
    }
}

class ChapterCountFilter : SelectFilter("Chapter Count", options) {
    companion object {
        private val options = listOf(
            SelectFilterOption("Any", ""),
            SelectFilterOption("0", "0"),
            SelectFilterOption("1+", "1"),
            SelectFilterOption("10+", "10"),
            SelectFilterOption("20+", "20"),
            SelectFilterOption("30+", "30"),
            SelectFilterOption("40+", "40"),
            SelectFilterOption("50+", "50"),
            SelectFilterOption("60+", "60"),
            SelectFilterOption("70+", "70"),
            SelectFilterOption("80+", "80"),
            SelectFilterOption("90+", "90"),
            SelectFilterOption("100+", "100"),
            SelectFilterOption("200+", "200"),
            SelectFilterOption("300+", "300"),
            SelectFilterOption("1~9", "1-9"),
            SelectFilterOption("10~19", "10-19"),
            SelectFilterOption("20~29", "20-29"),
            SelectFilterOption("30~39", "30-39"),
            SelectFilterOption("40~49", "40-49"),
            SelectFilterOption("50~59", "50-59"),
            SelectFilterOption("60~69", "60-69"),
            SelectFilterOption("70~79", "70-79"),
            SelectFilterOption("80~89", "80-89"),
            SelectFilterOption("90~99", "90-99"),
            SelectFilterOption("100~199", "100-199"),
            SelectFilterOption("200~299", "200-299"),
        )
    }
}

class OriginalLanguageFilter : CheckboxGroupFilter("Original Work Language", options) {
    companion object {
        private val options = listOf(
            CheckboxFilterOption("en", "English"),
            CheckboxFilterOption("zh", "Chinese"),
            CheckboxFilterOption("ja", "Japanese"),
            CheckboxFilterOption("ko", "Korean"),
            CheckboxFilterOption("af", "Afrikaans"),
            CheckboxFilterOption("sq", "Albanian"),
            CheckboxFilterOption("am", "Amharic"),
            CheckboxFilterOption("ar", "Arabic"),
            CheckboxFilterOption("hy", "Armenian"),
            CheckboxFilterOption("az", "Azerbaijani"),
            CheckboxFilterOption("be", "Belarusian"),
            CheckboxFilterOption("bn", "Bengali"),
            CheckboxFilterOption("bs", "Bosnian"),
            CheckboxFilterOption("bg", "Bulgarian"),
            CheckboxFilterOption("my", "Burmese"),
            CheckboxFilterOption("km", "Cambodian"),
            CheckboxFilterOption("ca", "Catalan"),
            CheckboxFilterOption("ceb", "Cebuano"),
            CheckboxFilterOption("zh_hk", "Chinese (Cantonese)"),
            CheckboxFilterOption("zh_tw", "Chinese (Traditional)"),
            CheckboxFilterOption("hr", "Croatian"),
            CheckboxFilterOption("cs", "Czech"),
            CheckboxFilterOption("da", "Danish"),
            CheckboxFilterOption("nl", "Dutch"),
            CheckboxFilterOption("et", "Estonian"),
            CheckboxFilterOption("fo", "Faroese"),
            CheckboxFilterOption("fil", "Filipino"),
            CheckboxFilterOption("fi", "Finnish"),
            CheckboxFilterOption("fr", "French"),
            CheckboxFilterOption("ka", "Georgian"),
            CheckboxFilterOption("de", "German"),
            CheckboxFilterOption("el", "Greek"),
            CheckboxFilterOption("gn", "Guarani"),
            CheckboxFilterOption("gu", "Gujarati"),
            CheckboxFilterOption("ht", "Haitian Creole"),
            CheckboxFilterOption("ha", "Hausa"),
            CheckboxFilterOption("he", "Hebrew"),
            CheckboxFilterOption("hi", "Hindi"),
            CheckboxFilterOption("hu", "Hungarian"),
            CheckboxFilterOption("is", "Icelandic"),
            CheckboxFilterOption("ig", "Igbo"),
            CheckboxFilterOption("id", "Indonesian"),
            CheckboxFilterOption("ga", "Irish"),
            CheckboxFilterOption("it", "Italian"),
            CheckboxFilterOption("jv", "Javanese"),
            CheckboxFilterOption("kn", "Kannada"),
            CheckboxFilterOption("kk", "Kazakh"),
            CheckboxFilterOption("ku", "Kurdish"),
            CheckboxFilterOption("ky", "Kyrgyz"),
            CheckboxFilterOption("lo", "Laothian"),
            CheckboxFilterOption("lv", "Latvian"),
            CheckboxFilterOption("lt", "Lithuanian"),
            CheckboxFilterOption("lb", "Luxembourgish"),
            CheckboxFilterOption("mk", "Macedonian"),
            CheckboxFilterOption("mg", "Malagasy"),
            CheckboxFilterOption("ms", "Malay"),
            CheckboxFilterOption("ml", "Malayalam"),
            CheckboxFilterOption("mt", "Maltese"),
            CheckboxFilterOption("mi", "Maori"),
            CheckboxFilterOption("mr", "Marathi"),
            CheckboxFilterOption("mo", "Moldavian"),
            CheckboxFilterOption("mn", "Mongolian"),
            CheckboxFilterOption("ne", "Nepali"),
            CheckboxFilterOption("no", "Norwegian"),
            CheckboxFilterOption("ny", "Nyanja"),
            CheckboxFilterOption("ps", "Pashto"),
            CheckboxFilterOption("fa", "Persian"),
            CheckboxFilterOption("pl", "Polish"),
            CheckboxFilterOption("pt", "Portuguese"),
            CheckboxFilterOption("pt_br", "Portuguese (Brazil)"),
            CheckboxFilterOption("ro", "Romanian"),
            CheckboxFilterOption("rm", "Romansh"),
            CheckboxFilterOption("ru", "Russian"),
            CheckboxFilterOption("sm", "Samoan"),
            CheckboxFilterOption("sr", "Serbian"),
            CheckboxFilterOption("sh", "Serbo-Croatian"),
            CheckboxFilterOption("st", "Sesotho"),
            CheckboxFilterOption("sn", "Shona"),
            CheckboxFilterOption("sd", "Sindhi"),
            CheckboxFilterOption("si", "Sinhalese"),
            CheckboxFilterOption("sk", "Slovak"),
            CheckboxFilterOption("sl", "Slovenian"),
            CheckboxFilterOption("so", "Somali"),
            CheckboxFilterOption("es", "Spanish"),
            CheckboxFilterOption("es_419", "Spanish (Latin America)"),
            CheckboxFilterOption("sw", "Swahili"),
            CheckboxFilterOption("sv", "Swedish"),
            CheckboxFilterOption("tg", "Tajik"),
            CheckboxFilterOption("ta", "Tamil"),
            CheckboxFilterOption("te", "Telugu"),
            CheckboxFilterOption("th", "Thai"),
            CheckboxFilterOption("ti", "Tigrinya"),
            CheckboxFilterOption("to", "Tonga"),
            CheckboxFilterOption("tr", "Turkish"),
            CheckboxFilterOption("tk", "Turkmen"),
            CheckboxFilterOption("uk", "Ukrainian"),
            CheckboxFilterOption("ur", "Urdu"),
            CheckboxFilterOption("uz", "Uzbek"),
            CheckboxFilterOption("vi", "Vietnamese"),
            CheckboxFilterOption("yo", "Yoruba"),
            CheckboxFilterOption("zu", "Zulu"),
            CheckboxFilterOption("_t", "Other"),
        )
    }
}

class TranslationLanguageFilter(siteLang: String) : CheckboxGroupFilter(
    "Translated Language",
    options.let { opts ->
        val siteOption = opts.find { it.value == siteLang }
        if (siteOption != null) {
            // Reorder options to put siteLang first, also default it to true
            listOf(CheckboxFilterOption(siteOption.value, siteOption.name, default = true)) +
                opts.filterNot { it.value == siteLang }
        } else {
            opts
        }
    },
) {
    companion object {
        private val options = listOf(
            CheckboxFilterOption("en", "English"),
            CheckboxFilterOption("zh", "Chinese"),
            CheckboxFilterOption("ja", "Japanese"),
            CheckboxFilterOption("ko", "Korean"),
            CheckboxFilterOption("af", "Afrikaans"),
            CheckboxFilterOption("sq", "Albanian"),
            CheckboxFilterOption("am", "Amharic"),
            CheckboxFilterOption("ar", "Arabic"),
            CheckboxFilterOption("hy", "Armenian"),
            CheckboxFilterOption("az", "Azerbaijani"),
            CheckboxFilterOption("be", "Belarusian"),
            CheckboxFilterOption("bn", "Bengali"),
            CheckboxFilterOption("bs", "Bosnian"),
            CheckboxFilterOption("bg", "Bulgarian"),
            CheckboxFilterOption("my", "Burmese"),
            CheckboxFilterOption("km", "Cambodian"),
            CheckboxFilterOption("ca", "Catalan"),
            CheckboxFilterOption("ceb", "Cebuano"),
            CheckboxFilterOption("zh_hk", "Chinese (Cantonese)"),
            CheckboxFilterOption("zh_tw", "Chinese (Traditional)"),
            CheckboxFilterOption("hr", "Croatian"),
            CheckboxFilterOption("cs", "Czech"),
            CheckboxFilterOption("da", "Danish"),
            CheckboxFilterOption("nl", "Dutch"),
            CheckboxFilterOption("et", "Estonian"),
            CheckboxFilterOption("fo", "Faroese"),
            CheckboxFilterOption("fil", "Filipino"),
            CheckboxFilterOption("fi", "Finnish"),
            CheckboxFilterOption("fr", "French"),
            CheckboxFilterOption("ka", "Georgian"),
            CheckboxFilterOption("de", "German"),
            CheckboxFilterOption("el", "Greek"),
            CheckboxFilterOption("gn", "Guarani"),
            CheckboxFilterOption("gu", "Gujarati"),
            CheckboxFilterOption("ht", "Haitian Creole"),
            CheckboxFilterOption("ha", "Hausa"),
            CheckboxFilterOption("he", "Hebrew"),
            CheckboxFilterOption("hi", "Hindi"),
            CheckboxFilterOption("hu", "Hungarian"),
            CheckboxFilterOption("is", "Icelandic"),
            CheckboxFilterOption("ig", "Igbo"),
            CheckboxFilterOption("id", "Indonesian"),
            CheckboxFilterOption("ga", "Irish"),
            CheckboxFilterOption("it", "Italian"),
            CheckboxFilterOption("jv", "Javanese"),
            CheckboxFilterOption("kn", "Kannada"),
            CheckboxFilterOption("kk", "Kazakh"),
            CheckboxFilterOption("ku", "Kurdish"),
            CheckboxFilterOption("ky", "Kyrgyz"),
            CheckboxFilterOption("lo", "Laothian"),
            CheckboxFilterOption("lv", "Latvian"),
            CheckboxFilterOption("lt", "Lithuanian"),
            CheckboxFilterOption("lb", "Luxembourgish"),
            CheckboxFilterOption("mk", "Macedonian"),
            CheckboxFilterOption("mg", "Malagasy"),
            CheckboxFilterOption("ms", "Malay"),
            CheckboxFilterOption("ml", "Malayalam"),
            CheckboxFilterOption("mt", "Maltese"),
            CheckboxFilterOption("mi", "Maori"),
            CheckboxFilterOption("mr", "Marathi"),
            CheckboxFilterOption("mo", "Moldavian"),
            CheckboxFilterOption("mn", "Mongolian"),
            CheckboxFilterOption("ne", "Nepali"),
            CheckboxFilterOption("no", "Norwegian"),
            CheckboxFilterOption("ny", "Nyanja"),
            CheckboxFilterOption("ps", "Pashto"),
            CheckboxFilterOption("fa", "Persian"),
            CheckboxFilterOption("pl", "Polish"),
            CheckboxFilterOption("pt", "Portuguese"),
            CheckboxFilterOption("pt_br", "Portuguese (Brazil)"),
            CheckboxFilterOption("ro", "Romanian"),
            CheckboxFilterOption("rm", "Romansh"),
            CheckboxFilterOption("ru", "Russian"),
            CheckboxFilterOption("sm", "Samoan"),
            CheckboxFilterOption("sr", "Serbian"),
            CheckboxFilterOption("sh", "Serbo-Croatian"),
            CheckboxFilterOption("st", "Sesotho"),
            CheckboxFilterOption("sn", "Shona"),
            CheckboxFilterOption("sd", "Sindhi"),
            CheckboxFilterOption("si", "Sinhalese"),
            CheckboxFilterOption("sk", "Slovak"),
            CheckboxFilterOption("sl", "Slovenian"),
            CheckboxFilterOption("so", "Somali"),
            CheckboxFilterOption("es", "Spanish"),
            CheckboxFilterOption("es_419", "Spanish (Latin America)"),
            CheckboxFilterOption("sw", "Swahili"),
            CheckboxFilterOption("sv", "Swedish"),
            CheckboxFilterOption("tg", "Tajik"),
            CheckboxFilterOption("ta", "Tamil"),
            CheckboxFilterOption("te", "Telugu"),
            CheckboxFilterOption("th", "Thai"),
            CheckboxFilterOption("ti", "Tigrinya"),
            CheckboxFilterOption("to", "Tonga"),
            CheckboxFilterOption("tr", "Turkish"),
            CheckboxFilterOption("tk", "Turkmen"),
            CheckboxFilterOption("uk", "Ukrainian"),
            CheckboxFilterOption("ur", "Urdu"),
            CheckboxFilterOption("uz", "Uzbek"),
            CheckboxFilterOption("vi", "Vietnamese"),
            CheckboxFilterOption("yo", "Yoruba"),
            CheckboxFilterOption("zu", "Zulu"),
            CheckboxFilterOption("_t", "Other"),
        )
    }
}

class GenreGroupFilter : TriStateGroupFilter("Genres", options) {
    companion object {
        val options = listOf(
            TriStateFilterOption("artbook", "Artbook"),
            TriStateFilterOption("cartoon", "Cartoon"),
            TriStateFilterOption("comic", "Comic"),
            TriStateFilterOption("doujinshi", "Doujinshi"),
            TriStateFilterOption("imageset", "Imageset"),
            TriStateFilterOption("manga", "Manga"),
            TriStateFilterOption("manhua", "Manhua"),
            TriStateFilterOption("manhwa", "Manhwa"),
            TriStateFilterOption("webtoon", "Webtoon"),
            TriStateFilterOption("western", "Western"),
            TriStateFilterOption("_4_koma", "4-Koma"),
            TriStateFilterOption("oneshot", "Oneshot"),

            TriStateFilterOption("shoujo", "Shoujo(G)"),
            TriStateFilterOption("shounen", "Shounen(B)"),
            TriStateFilterOption("josei", "Josei(W)"),
            TriStateFilterOption("seinen", "Seinen(M)"),
            TriStateFilterOption("yuri", "Yuri(GL)"),
            TriStateFilterOption("yaoi", "Yaoi(BL)"),
            // TriStateFilterOption("futa", "Futa(WL)"), // May not exist?
            TriStateFilterOption("bara", "Bara(ML)"),
            TriStateFilterOption("kodomo", "Kodomo(Kid)"),
            TriStateFilterOption("old_people", "Silver & Golden"),
            TriStateFilterOption("shoujo_ai", "Shoujo Ai"),
            TriStateFilterOption("shounen_ai", "Shounen Ai"),
            TriStateFilterOption("non_human", "Non-human"),

            TriStateFilterOption("gore", "Gore"),
            TriStateFilterOption("bloody", "Bloody"),
            TriStateFilterOption("violence", "Violence"),
            TriStateFilterOption("ecchi", "Ecchi"),
            TriStateFilterOption("adult", "Adult"),
            TriStateFilterOption("mature", "Mature"),
            TriStateFilterOption("smut", "Smut"),
            TriStateFilterOption("hentai", "Hentai"),

            TriStateFilterOption("action", "Action"),
            TriStateFilterOption("adaptation", "Adaptation"),
            TriStateFilterOption("adventure", "Adventure"),
            TriStateFilterOption("age_gap", "Age Gap"),
            TriStateFilterOption("aliens", "Aliens"),
            TriStateFilterOption("animals", "Animals"),
            TriStateFilterOption("anthology", "Anthology"),
            TriStateFilterOption("beasts", "Beasts"),
            TriStateFilterOption("bodyswap", "Bodyswap"),
            TriStateFilterOption("boys", "Boys"),
            TriStateFilterOption("cars", "cars"),
            TriStateFilterOption("cheating_infidelity", "Cheating/Infidelity"),
            TriStateFilterOption("childhood_friends", "Childhood Friends"),
            TriStateFilterOption("college_life", "College Life"),
            TriStateFilterOption("comedy", "Comedy"),
            TriStateFilterOption("contest_winning", "Contest Winning"),
            TriStateFilterOption("cooking", "Cooking"),
            TriStateFilterOption("crime", "crime"),
            TriStateFilterOption("crossdressing", "Crossdressing"),
            TriStateFilterOption("delinquents", "Delinquents"),
            TriStateFilterOption("dementia", "Dementia"),
            TriStateFilterOption("demons", "Demons"),
            TriStateFilterOption("drama", "Drama"),
            TriStateFilterOption("dungeons", "Dungeons"),
            TriStateFilterOption("emperor_daughte", "Emperor's Daughter"),
            TriStateFilterOption("fantasy", "Fantasy"),
            TriStateFilterOption("fan_colored", "Fan-Colored"),
            TriStateFilterOption("fetish", "Fetish"),
            TriStateFilterOption("full_color", "Full Color"),
            TriStateFilterOption("game", "Game"),
            TriStateFilterOption("gender_bender", "Gender Bender"),
            TriStateFilterOption("genderswap", "Genderswap"),
            TriStateFilterOption("girls", "Girls"),
            TriStateFilterOption("ghosts", "Ghosts"),
            TriStateFilterOption("gyaru", "Gyaru"),
            TriStateFilterOption("harem", "Harem"),
            TriStateFilterOption("harlequin", "Harlequin"),
            TriStateFilterOption("historical", "Historical"),
            TriStateFilterOption("horror", "Horror"),
            TriStateFilterOption("incest", "Incest"),
            TriStateFilterOption("isekai", "Isekai"),
            TriStateFilterOption("kids", "Kids"),
            TriStateFilterOption("magic", "Magic"),
            TriStateFilterOption("magical_girls", "Magical Girls"),
            TriStateFilterOption("martial_arts", "Martial Arts"),
            TriStateFilterOption("mecha", "Mecha"),
            TriStateFilterOption("medical", "Medical"),
            TriStateFilterOption("military", "Military"),
            TriStateFilterOption("monster_girls", "Monster Girls"),
            TriStateFilterOption("monsters", "Monsters"),
            TriStateFilterOption("music", "Music"),
            TriStateFilterOption("mystery", "Mystery"),
            TriStateFilterOption("netori", "Netori"),
            TriStateFilterOption("netorare", "Netorare/NTR"),
            TriStateFilterOption("ninja", "Ninja"),
            TriStateFilterOption("office_workers", "Office Workers"),
            TriStateFilterOption("omegaverse", "Omegaverse"),
            TriStateFilterOption("parody", "parody"),
            TriStateFilterOption("philosophical", "Philosophical"),
            TriStateFilterOption("police", "Police"),
            TriStateFilterOption("post_apocalyptic", "Post-Apocalyptic"),
            TriStateFilterOption("psychological", "Psychological"),
            TriStateFilterOption("regression", "Regression"),
            TriStateFilterOption("reincarnation", "Reincarnation"),
            TriStateFilterOption("reverse_harem", "Reverse Harem"),
            TriStateFilterOption("revenge", "Revenge"),
            TriStateFilterOption("reverse_isekai", "Reverse Isekai"),
            TriStateFilterOption("romance", "Romance"),
            TriStateFilterOption("royal_family", "Royal Family"),
            TriStateFilterOption("royalty", "Royalty"),
            TriStateFilterOption("samurai", "Samurai"),
            TriStateFilterOption("school_life", "School Life"),
            TriStateFilterOption("sci_fi", "Sci-Fi"),
            TriStateFilterOption("shota", "Shota"),
            TriStateFilterOption("showbiz", "Showbiz"),
            TriStateFilterOption("slice_of_life", "Slice of Life"),
            TriStateFilterOption("sm_bdsm", "SM/BDSM/SUB-DOM"),
            TriStateFilterOption("space", "Space"),
            TriStateFilterOption("sports", "Sports"),
            TriStateFilterOption("super_power", "Super Power"),
            TriStateFilterOption("superhero", "Superhero"),
            TriStateFilterOption("supernatural", "Supernatural"),
            TriStateFilterOption("survival", "Survival"),
            TriStateFilterOption("thriller", "Thriller"),
            TriStateFilterOption("time_travel", "Time Travel"),
            TriStateFilterOption("tower_climbing", "Tower Climbing"),
            TriStateFilterOption("traditional_games", "Traditional Games"),
            TriStateFilterOption("tragedy", "Tragedy"),
            TriStateFilterOption("transmigration", "Transmigration"),
            TriStateFilterOption("vampires", "Vampires"),
            TriStateFilterOption("villainess", "Villainess"),
            TriStateFilterOption("video_games", "Video Games"),
            TriStateFilterOption("virtual_reality", "Virtual Reality"),
            TriStateFilterOption("wuxia", "Wuxia"),
            TriStateFilterOption("xianxia", "Xianxia"),
            TriStateFilterOption("xuanhuan", "Xuanhuan"),
            TriStateFilterOption("yakuzas", "Yakuzas"),
            TriStateFilterOption("zombies", "Zombies"),
            // Hidden Genres (Probably don't exist)
            TriStateFilterOption("shotacon", "shotacon"),
            TriStateFilterOption("loli", "Loli"),
            TriStateFilterOption("lolicon", "lolicon"),
            TriStateFilterOption("award_winning", "Award Winning"),
            TriStateFilterOption("youkai", "Youkai"),
            TriStateFilterOption("uncategorized", "Uncategorized"),
        )
    }
}
