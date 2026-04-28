package eu.kanade.tachiyomi.extension.id.doujindesuunoriginal

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter(
    state: String? = sortValues[0].second,
) : Filter.Select<String>(
    name = "Urutkan",
    values = sortValues.map { it.first }.toTypedArray(),
    state = sortValues.indexOfFirst { it.second == state }.takeIf { it != -1 } ?: 0,
) {
    val sort get() = sortValues[state].second

    companion object {
        val popular get() = FilterList(SortFilter("popular"))
        val latest get() = FilterList(SortFilter("latest"))
    }
}

private val sortValues = listOf(
    "Semua" to null,
    "Update" to "latest",
    "Populer" to "popular",
    "A-Z" to "az",
    "Z-A" to "za",
)

class StatusFilter :
    Filter.Select<String>(
        name = "Status",
        values = statusValues.map { it.first }.toTypedArray(),
    ) {
    val status get() = statusValues[state].second
}

private val statusValues = listOf(
    "Semua" to null,
    "Ongoing" to "publishing",
    "Completed" to "finished",
)

class TypeFilter :
    Filter.Select<String>(
        name = "Tipe",
        values = typeValues.map { it.first }.toTypedArray(),
    ) {
    val type get() = typeValues[state].second
}

private val typeValues = listOf(
    "Semua" to null,
    "Manga" to "manga",
    "Manhwa" to "manhwa",
    "Doujinshi" to "doujinshi",
)

class GenreFilter :
    Filter.Select<String>(
        "Genre",
        genreValues.map { it.first }.toTypedArray(),
    ) {
    val genre get() = genreValues[state].second
}

private val genreValues = listOf(
    "Semua" to null,
    "Age Progression" to "Age Progression",
    "Age Regression" to "Age Regression",
    "Aheago" to "Aheago",
    "Ahegao" to "Ahegao",
    "Anal" to "Anal",
    "Apron" to "Apron",
    "Aunt" to "Aunt",
    "Bald" to "Bald",
    "Bestiality" to "Bestiality",
    "Big Ass" to "Big Ass",
    "Big Breast" to "Big Breast",
    "Big Penis" to "Big Penis",
    "Bike Shorts" to "Bike Shorts",
    "Bikini" to "Bikini",
    "Birth" to "Birth",
    "Bisexual" to "Bisexual",
    "Blackmail" to "Blackmail",
    "Blindfold" to "Blindfold",
    "Bloomers" to "Bloomers",
    "Blowjob" to "Blowjob",
    "Body Swap" to "Body Swap",
    "Bodysuit" to "Bodysuit",
    "Bondage" to "Bondage",
    "Business Suit" to "Business Suit",
    "Cheating" to "Cheating",
    "Collar" to "Collar",
    "Condom" to "Condom",
    "Cousin" to "Cousin",
    "Crossdressing" to "Crossdressing",
    "Cunnilingus" to "Cunnilingus",
    "DILF" to "DILF",
    "Dark Skin" to "Dark Skin",
    "Daughter" to "Daughter",
    "Defloration" to "Defloration",
    "Demon Girl" to "Demon Girl",
    "Dick Growth" to "Dick Growth",
    "Double Penetration" to "Double Penetration",
    "Drugs" to "Drugs",
    "Drunk" to "Drunk",
    "Elf" to "Elf",
    "Emotionless Sex" to "Emotionless Sex",
    "Exhibitionism" to "Exhibitionism",
    "Eyepatch" to "Eyepatch",
    "Females Only" to "Females Only",
    "Femdom" to "Femdom",
    "Filming" to "Filming",
    "Fingering" to "Fingering",
    "Footjob" to "Footjob",
    "Full Color" to "Full Color",
    "Furry" to "Furry",
    "Futanari" to "Futanari",
    "Garter Belt" to "Garter Belt",
    "Gender Bender" to "Gender Bender",
    "Ghost" to "Ghost",
    "Glasses" to "Glasses",
    "Group" to "Group",
    "Gyaru" to "Gyaru",
    "Hairy" to "Hairy",
    "Handjob" to "Handjob",
    "Harem" to "Harem",
    "Horns" to "Horns",
    "Huge Breast" to "Huge Breast",
    "Humiliation" to "Humiliation",
    "Impregnation" to "Impregnation",
    "Incest" to "Incest",
    "Inflation" to "Inflation",
    "Inseki" to "Inseki",
    "Inverted Nipples" to "Inverted Nipples",
    "Kemomimi" to "Kemomimi",
    "Kimono" to "Kimono",
    "Lactation" to "Lactation",
    "Leotard" to "Leotard",
    "Lingerie" to "Lingerie",
    "Loli" to "Loli",
    "Lolipai" to "Lolipai",
    "MILF" to "MILF",
    "Maid" to "Maid",
    "Males Only" to "Males Only",
    "Masturbation" to "Masturbation",
    "Miko" to "Miko",
    "Mind Break" to "Mind Break",
    "Mind Control" to "Mind Control",
    "Monster Girl" to "Monster Girl",
    "Mother" to "Mother",
    "Multi-work Series" to "Multi-work Series",
    "Muscle" to "Muscle",
    "Nakadashi" to "Nakadashi",
    "Netorare" to "Netorare",
    "Niece" to "Niece",
    "Nipple Fuck" to "Nipple Fuck",
    "Nurse" to "Nurse",
    "Old Man" to "Old Man",
    "Oyakodon" to "Oyakodon",
    "Paizuri" to "Paizuri",
    "Pantyhose" to "Pantyhose",
    "Possession" to "Possession",
    "Pregnant" to "Pregnant",
    "Prostitution" to "Prostitution",
    "Rape" to "Rape",
    "Rimjob" to "Rimjob",
    "Scat" to "Scat",
    "School Uniform" to "School Uniform",
    "Sex Toys" to "Sex Toys",
    "Shemale" to "Shemale",
    "Shota" to "Shota",
    "Sister" to "Sister",
    "Sleeping" to "Sleeping",
    "Small Breast" to "Small Breast",
    "Snuff" to "Snuff",
    "Sole Female" to "Sole Female",
    "Sole Male" to "Sole Male",
    "Stocking" to "Stocking",
    "Story Arc" to "Story Arc",
    "Sumata" to "Sumata",
    "Sweating" to "Sweating",
    "Swimsuit" to "Swimsuit",
    "Tanlines" to "Tanlines",
    "Teacher" to "Teacher",
    "Tentacles" to "Tentacles",
    "Tomboy" to "Tomboy",
    "Tomgirl" to "Tomgirl",
    "Twins" to "Twins",
    "Twintails" to "Twintails",
    "Uncensored" to "Uncensored",
    "Unusual Pupils" to "Unusual Pupils",
    "Virginity" to "Virginity",
    "Webtoon" to "Webtoon",
    "Widow" to "Widow",
    "X-Ray" to "X-Ray",
    "Yandere" to "Yandere",
    "Yaoi" to "Yaoi",
    "Yuri" to "Yuri",
)
