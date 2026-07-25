package eu.kanade.tachiyomi.extension.vi.vinahentai

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response

fun getFilters(genres: List<Pair<String, String>>): FilterList = FilterList(
    buildList {
        val availableGenres = genres.ifEmpty { getGenreList() }
        if (availableGenres.isNotEmpty()) {
            add(GenreFilter(availableGenres))
        }
        add(SortFilter())
        add(StatusFilter())
    },
)

fun parseGenresFromHtml(response: Response): List<Pair<String, String>> {
    val document = response.asJsoup()
    val seenSlugs = mutableSetOf<String>()

    return document.select("a[href*=/genres/]")
        .mapNotNull { element ->
            val slug = element.attr("href")
                .substringAfter("/genres/")
                .substringBefore("?")
                .substringBefore("/")
            val name = element.text().trim()
            if (slug.isNotEmpty() && name.isNotEmpty() && seenSlugs.add(slug)) {
                Pair(name, slug)
            } else {
                null
            }
        }
        .sortedBy { it.first.lowercase() }
}

class GenreFilter(private val genres: List<Pair<String, String>>) :
    Filter.Select<String>(
        "Thể loại",
        arrayOf("Tất cả") + genres.map { it.first }.toTypedArray(),
    ) {
    val selected get() = if (state == 0) null else genres[state - 1].second
}

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp theo",
        arrayOf("Mới cập nhật", "Xem nhiều", "Đánh giá cao", "Cũ nhất"),
    ) {
    fun toUriPart() = when (state) {
        1 -> "views"
        2 -> "likes"
        3 -> "oldest"
        else -> "updatedAt"
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Tình trạng",
        arrayOf("Tất cả", "Đang tiến hành", "Đã hoàn thành"),
    ) {
    fun toUriPart() = when (state) {
        1 -> "ongoing"
        2 -> "completed"
        else -> ""
    }
}

private fun getGenreList(): List<Pair<String, String>> = listOf(
    Pair("3D Hentai", "3d-hentai"),
    Pair("Action", "action"),
    Pair("Adult", "adult"),
    Pair("Adventure", "adventure"),
    Pair("Ahegao", "ahegao"),
    Pair("AI Generated", "ai-generated"),
    Pair("Anal", "anal"),
    Pair("Angel", "angel"),
    Pair("Ảnh động", "anh-dong"),
    Pair("Animal", "animal"),
    Pair("Animal Girl", "animal-girl"),
    Pair("Áo dài", "ao-dai"),
    Pair("Apron", "apron"),
    Pair("Artist CG", "artist-cg"),
    Pair("Based Game", "based-game"),
    Pair("BBM", "bbm"),
    Pair("BBW", "bbw"),
    Pair("BDSM", "bdsm"),
    Pair("Beach", "beach"),
    Pair("Bestiality", "bestiality"),
    Pair("Big Ass", "big-ass"),
    Pair("Big Boobs", "big-boobs"),
    Pair("Big Penis", "big-penis"),
    Pair("Bikini", "bikini"),
    Pair("Bisexual", "bisexual"),
    Pair("Black Skin", "black-skin"),
    Pair("Blackmail", "blackmail"),
    Pair("Blindfold", "blindfold"),
    Pair("Bloomers", "bloomers"),
    Pair("BlowJobs", "blowjobs"),
    Pair("Body Swap", "body-swap"),
    Pair("Body Writing", "body-writing"),
    Pair("Bodysuit", "bodysuit"),
    Pair("Bondage", "bondage"),
    Pair("Breast Sucking", "breast-sucking"),
    Pair("BreastJobs", "breastjobs"),
    Pair("Brocon", "brocon"),
    Pair("Brother", "brother"),
    Pair("Bukkake", "bukkake"),
    Pair("Bunny Girl", "bunny-girl"),
    Pair("Business Suit", "business-suit"),
    Pair("Chastity belt", "chastity-belt"),
    Pair("Che ít", "che-it"),
    Pair("Che nhiều", "che-nhieu"),
    Pair("Cheating", "cheating"),
    Pair("Cheerleader", "cheerleader"),
    Pair("Chikan", "chikan"),
    Pair("Chinese Dress", "chinese-dress"),
    Pair("Có che", "co-che"),
    Pair("Collar", "collar"),
    Pair("Comedy", "comedy"),
    Pair("Comic", "comic"),
    Pair("Condom", "condom"),
    Pair("Cosplay", "cosplay"),
    Pair("Cousin", "cousin"),
    Pair("Creampie", "creampie"),
    Pair("Cross-dressing", "cross-dressing"),
    Pair("Crotch Tattoo", "crotch-tattoo"),
    Pair("Cuckold", "cuckold"),
    Pair("Cum swap", "cum-swap"),
    Pair("Cunnilingus", "cunnilingus"),
    Pair("Dark Skin", "dark-skin"),
    Pair("Daughter", "daughter"),
    Pair("Deepthroat", "deepthroat"),
    Pair("Demon", "demon"),
    Pair("DemonGirl", "demongirl"),
    Pair("Devil", "devil"),
    Pair("DevilGirl", "devilgirl"),
    Pair("Dirty", "dirty"),
    Pair("DirtyOldMan", "dirtyoldman"),
    Pair("Double Penetration", "double-penetration"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drama", "drama"),
    Pair("Drug", "drug"),
    Pair("Drunk", "drunk"),
    Pair("Ecchi", "ecchi"),
    Pair("Elder Sister", "elder-sister"),
    Pair("Elf", "elf"),
    Pair("Exhibitionism", "exhibitionism"),
    Pair("Facesitting", "facesitting"),
    Pair("Fantasy", "fantasy"),
    Pair("Father", "father"),
    Pair("Females only", "females-only"),
    Pair("Femdom", "femdom"),
    Pair("Feminization", "feminization"),
    Pair("Fingering", "fingering"),
    Pair("Footjob", "footjob"),
    Pair("Full Color", "full-color"),
    Pair("Furry", "furry"),
    Pair("Futanari", "futanari"),
    Pair("Gag", "gag"),
    Pair("Gangbang", "gangbang"),
    Pair("Garter Belts", "garter-belts"),
    Pair("Gender Bender", "gender-bender"),
    Pair("Ghost", "ghost"),
    Pair("Glasses", "glasses"),
    Pair("Glory hole", "glory-hole"),
    Pair("Góc Nhìn Nữ", "goc-nhin-nu"),
    Pair("Gothic Lolita", "gothic-lolita"),
    Pair("Group", "group"),
    Pair("Guro", "guro"),
    Pair("Hairjob", "hairjob"),
    Pair("Hairy", "hairy"),
    Pair("Handjob", "handjob"),
    Pair("Harem", "harem"),
    Pair("Hell No", "hell-no"),
    Pair("Hentaivn", "hentaivn"),
    Pair("Hidden sex", "hidden-sex"),
    Pair("Historical", "historical"),
    Pair("Horror", "horror"),
    Pair("Housewife", "housewife"),
    Pair("Humiliation", "humiliation"),
    Pair("Idol", "idol"),
    Pair("Imouto", "imouto"),
    Pair("Incest", "incest"),
    Pair("Insect", "insect"),
    Pair("Isekai", "isekai"),
    Pair("Không che", "hentai-khong-che"),
    Pair("Kimono", "kimono"),
    Pair("Kissing", "kissing"),
    Pair("Kuudere", "kuudere"),
    Pair("Lingerie", "lingerie"),
    Pair("Lolicon", "lolicon"),
    Pair("Maids", "maids"),
    Pair("Males only", "males-only"),
    Pair("Manhua", "manhua"),
    Pair("Manhwa", "manhwa"),
    Pair("Masturbation", "masturbation"),
    Pair("Mature", "mature"),
    Pair("Mermaid", "mermaid"),
    Pair("Miko", "miko"),
    Pair("Milf", "milf"),
    Pair("Mind Break", "mind-break"),
    Pair("Mind Control", "mind-control"),
    Pair("Monster", "monster"),
    Pair("Monstergirl", "monstergirl"),
    Pair("Mother", "mother"),
    Pair("Nakadashi", "nakadashi"),
    Pair("Netori", "netori"),
    Pair("Ngọt", "ngot"),
    Pair("Non-hen", "non-hen"),
    Pair("NTR", "ntr"),
    Pair("Nun", "nun"),
    Pair("Nurse", "nurse"),
    Pair("Office Lady", "office-lady"),
    Pair("Old Man", "old-man"),
    Pair("Oneshot", "oneshot"),
    Pair("Oral", "oral"),
    Pair("Osananajimi", "osananajimi"),
    Pair("Paizuri", "paizuri"),
    Pair("Pantyhose", "pantyhose"),
    Pair("Pegging", "pegging"),
    Pair("Piercing", "piercing"),
    Pair("Police", "police"),
    Pair("Ponytail", "ponytail"),
    Pair("Pregnant", "pregnant"),
    Pair("Princess", "princess"),
    Pair("Rape", "rape"),
    Pair("Rimjob", "rimjob"),
    Pair("Romance", "romance"),
    Pair("Ryona", "ryona"),
    Pair("Scat", "scat"),
    Pair("School uniform", "school-uniform"),
    Pair("SchoolGirl", "schoolgirl"),
    Pair("Series", "series"),
    Pair("Sex Toys", "sex-toys"),
    Pair("Shimapan", "shimapan"),
    Pair("Short", "short"),
    Pair("Shota", "shota"),
    Pair("Shoujo", "shoujo"),
    Pair("Siscon", "siscon"),
    Pair("Sister", "sister"),
    Pair("Sixty-Nine", "sixty-nine"),
    Pair("Slave", "slave"),
    Pair("Sleeping", "sleeping"),
    Pair("Small Boobs", "small-boobs"),
    Pair("Soft Incest", "soft-incest"),
    Pair("Son", "son"),
    Pair("Spanking", "spanking"),
    Pair("Sport", "sport"),
    Pair("Squirting", "squirting"),
    Pair("Stockings", "stockings"),
    Pair("Strap-on", "strap-on"),
    Pair("Succubus", "succubus"),
    Pair("Supernatural", "supernatural"),
    Pair("Sweating", "sweating"),
    Pair("Swimsuit", "swimsuit"),
    Pair("Tail plug", "tail-plug"),
    Pair("Tall Girl", "tall-girl"),
    Pair("Teacher", "teacher"),
    Pair("Tentacles", "tentacles"),
    Pair("Threesome", "threesome"),
    Pair("Time Stop", "time-stop"),
    Pair("Tomboy", "tomboy"),
    Pair("Tracksuit", "tracksuit"),
    Pair("Transformation", "transformation"),
    Pair("Trap", "trap"),
    Pair("Truyện Việt", "truyen-viet"),
    Pair("Tsundere", "tsundere"),
    Pair("Tu Tiên", "tu-tien"),
    Pair("Twins", "twins"),
    Pair("Twintails", "twintails"),
    Pair("Underwater", "underwater"),
    Pair("Vampire", "vampire"),
    Pair("Vanilla", "vanilla"),
    Pair("Virgin", "virgin"),
    Pair("Vtuber", "vtuber"),
    Pair("Webtoon", "webtoon"),
    Pair("Wormhole", "wormhole"),
    Pair("X-ray", "x-ray"),
    Pair("Yandere", "yandere"),
    Pair("Yaoi", "yaoi"),
    Pair("Yuri", "yuri"),
    Pair("Zombie", "zombie"),
)
