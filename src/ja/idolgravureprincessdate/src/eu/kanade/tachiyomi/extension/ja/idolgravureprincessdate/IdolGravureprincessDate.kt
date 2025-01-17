package eu.kanade.tachiyomi.extension.ja.idolgravureprincessdate

import eu.kanade.tachiyomi.multisrc.gravureblogger.GravureBlogger

class IdolGravureprincessDate :
    GravureBlogger(
        "Idol. gravureprincess .date",
        "https://idol.gravureprincess.date",
        "ja",
    ) {
    override val labelFilters =
        buildMap {
            put("Idol", getIdols())
            put("Magazines", getMagazine())
        }

    private fun getIdols() =
        listOf(
            "Nogizaka46",
            "AKB48",
            "NMB48",
            "Keyakizaka46",
            "HKT48",
            "SKE48",
            "NGT48",
            "SUPER☆GiRLS",
            "Morning Musume",
            "Dempagumi.inc",
            "Angerme",
            "Juice=Juice",
            "NijiCon-虹コン",
            "Houkago Princess",
            "Magical Punchline",
            "Idoling!!!",
            "Rev. from DVL",
            "Link STAR`s",
            "LADYBABY",
            "℃-ute",
            "Country Girls",
            "Up Up Girls (Kakko Kari)",
            "Yumemiru Adolescence",
            "Shiritsu Ebisu Chugaku",
            "Tenkoushoujo Kagekidan",
            "Drop",
            "Steam Girls",
            "Kamen Joshi's",
            "LinQ",
            "Doll☆Element",
            "TrySail",
            "Akihabara Backstage Pass",
            "Palet",
            "Passport☆",
            "Ange☆Reve",
            "BiSH",
            "Ciao Bella Cinquetti",
            "Gekidanherbest",
            "Haraeki Stage Ace",
            "Ru:Run",
            "SDN48",
        )

    private fun getMagazine() =
        listOf(
            "FLASH",
            "Weekly Playboy",
            "FRIDAY Magazine",
            "Young Jump",
            "Young Magazine",
            "BLT",
            "ENTAME",
            "EX-Taishu",
            "SPA! Magazine",
            "Young Gangan",
            "UTB",
            "Young Animal",
            "Young Champion",
            "Big Comic Spirtis",
            "Shonen Magazine",
            "BUBKA",
            "BOMB",
            "Shonen Champion",
            "Manga Action",
            "Weekly Shonen Sunday",
            "Photobooks",
            "BRODY",
            "Hustle Press",
            "ANAN Magazine",
            "SMART Magazine",
            "Young Sunday",
            "Gravure The Television",
            "CD&DL My Girl",
            "Daily LoGiRL",
            "Shukan Taishu",
            "Girls! Magazine",
            "Soccer Game King",
            "Weekly Georgia",
            "Sunday Magazine",
            "Mery Magazine",
        )
}
