package eu.kanade.tachiyomi.extension.vi.nhentaiclub

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters(): FilterList = FilterList(
    SortFilter(),
    StatusFilter(),
    GenreFilter(),
)

class SortFilter :
    Filter.Select<String>(
        "Sắp xếp theo",
        arrayOf("Mới cập nhật", "Xem nhiều"),
    ) {
    fun toUriPart(): String = when (state) {
        1 -> "view"
        else -> "recent-update"
    }
}

class StatusFilter :
    Filter.Select<String>(
        "Trạng thái",
        arrayOf("Tất cả", "Hoàn thành", "Đang tiến hành"),
    ) {
    fun toUriPart(): String? = when (state) {
        1 -> "completed"
        2 -> "progress"
        else -> null
    }
}

class GenreFilter :
    Filter.Select<String>(
        "Thể loại",
        GENRE_LIST.map { it.first }.toTypedArray(),
    ) {
    fun toUriPart(): String = GENRE_LIST[state].second
}

private val GENRE_LIST: List<Pair<String, String>> = listOf(
    Pair("Tất cả", "all"),
    Pair("Ahegao", "ahegao"),
    Pair("Anal", "anal"),
    Pair("Angel", "angel"),
    Pair("Animal", "animal"),
    Pair("Apron", "apron"),
    Pair("Armpit licking", "armpit-licking"),
    Pair("Aunt", "aunt"),
    Pair("Bald", "bald"),
    Pair("BDSM", "bdsm"),
    Pair("Blindfold", "blindfold"),
    Pair("Big Ass", "big-ass"),
    Pair("Big Boobs", "big-boobs"),
    Pair("Bunny Girl", "bunny-girl"),
    Pair("Bikini", "bikini"),
    Pair("Big Penis", "big-penis"),
    Pair("Black Mail", "black-mail"),
    Pair("Bloomers", "bloomers"),
    Pair("BlowJobs", "blowjobs"),
    Pair("Body Swap", "body-swap"),
    Pair("Bodysuit", "bodysuit"),
    Pair("Breast Sucking", "breast-sucking"),
    Pair("Bride", "bride"),
    Pair("Brother", "brother"),
    Pair("Business Suit", "business-suit"),
    Pair("Catgirl", "catgirl"),
    Pair("Cheating", "cheating"),
    Pair("Chikan", "chikan"),
    Pair("Chinese Dress", "chinese-dress"),
    Pair("Collar", "collar"),
    Pair("Condom", "condom"),
    Pair("Cosplay", "cosplay"),
    Pair("Cousin", "cousin"),
    Pair("Dark skin", "dark-skin"),
    Pair("Daughter", "daughter"),
    Pair("Deepthroat", "deep-throat"),
    Pair("Demon Girl", "demon-girl"),
    Pair("Defloration", "defloration"),
    Pair("Dog Girl", "dog-girl"),
    Pair("Double Penetration", "double-penetration"),
    Pair("Doujinshi", "doujinshi"),
    Pair("Drugs", "drugs"),
    Pair("Drunk", "drunk"),
    Pair("Dilf", "dilf"),
    Pair("Elf", "elf"),
    Pair("Emotionless Sex", "emotionless-sex"),
    Pair("Exhibitionism", "exhibitionism"),
    Pair("Father", "father"),
    Pair("Femdom", "femdom"),
    Pair("Fingering", "fingering"),
    Pair("Footjob", "footjob"),
    Pair("Foot Licking", "foot-licking"),
    Pair("Fox Girl", "fox-girl"),
    Pair("Full Color", "full-color"),
    Pair("Futanari", "futanari"),
    Pair("Glasses", "glasses"),
    Pair("Garter belt", "garter-belter"),
    Pair("Group", "group"),
    Pair("Goblin", "goblin"),
    Pair("Guro", "guro"),
    Pair("Hairy", "hairy"),
    Pair("Handjob", "handjob"),
    Pair("Harem", "harem"),
    Pair("Hidden", "hidden"),
    Pair("Humiliation", "humiliation"),
    Pair("Impregnation", "impregnation"),
    Pair("Incest", "incest"),
    Pair("Insect", "insect"),
    Pair("Kimono", "kimono"),
    Pair("Kissing", "kissing"),
    Pair("Lactation", "lactation"),
    Pair("Leg Log", "leg-log"),
    Pair("Lolicon", "lolicon"),
    Pair("Manhwa", "manhwa"),
    Pair("Maid", "maid"),
    Pair("Masturbation", "masturbation"),
    Pair("Miko", "miko"),
    Pair("Milf", "milf"),
    Pair("Mind Break", "mind-break"),
    Pair("Mind Control", "mind-control"),
    Pair("Monster", "monster"),
    Pair("Monster Girl", "monster-girl"),
    Pair("Mother", "mother"),
    Pair("Muscle", "muscle"),
    Pair("Nakadashi", "nakadashi"),
    Pair("Netori", "netori"),
    Pair("NTR(netorare)", "netorare"),
    Pair("Nurse", "nurse"),
    Pair("Old man", "old-man"),
    Pair("Oneshot", "oneshot"),
    Pair("Orc", "orc"),
    Pair("Pantyhose", "pantyhose"),
    Pair("Paizuri", "paizuri"),
    Pair("Ponytail", "ponytail"),
    Pair("Pregnant", "pregnant"),
    Pair("Rape", "rape"),
    Pair("Rimjob", "rimjob"),
    Pair("Ryona", "ryona"),
    Pair("Schoolgirl Uniform", "school-girl-uniform"),
    Pair("Series", "series"),
    Pair("Sex Toys", "sex-toys"),
    Pair("Sleeping", "sleeping"),
    Pair("Simapan", "simapan"),
    Pair("Shotacon", "shotacon"),
    Pair("Sister", "sister"),
    Pair("Slave", "slave"),
    Pair("Small Boobs", "small-boobs"),
    Pair("Stockings", "stockings"),
    Pair("Sweating", "sweating"),
    Pair("Swimsuit", "swimsuit"),
    Pair("Tall Girl", "tall-girl"),
    Pair("Teacher", "teacher"),
    Pair("Tentacles", "tentacles"),
    Pair("Threesome", "three-some"),
    Pair("Time Stop", "time-stop"),
    Pair("Tomboy", "tomboy"),
    Pair("Tomgirl", "tomgirl"),
    Pair("Tracksuit", "tracksuit"),
    Pair("Transformation", "transformation"),
    Pair("Twins", "twins"),
    Pair("Twintails", "twintails"),
    Pair("Uncle", "uncle"),
    Pair("Vampire", "vampire"),
    Pair("Virgin", "virgin"),
    Pair("X-ray", "x-ray"),
    Pair("Yandere", "yandere"),
    Pair("Yaoi", "yaoi"),
    Pair("Yuri", "yuri"),
    Pair("Zombie", "zombie"),
    Pair("3D", "3d"),
)
