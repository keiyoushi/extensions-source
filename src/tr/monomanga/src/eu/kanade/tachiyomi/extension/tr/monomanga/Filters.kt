package eu.kanade.tachiyomi.extension.tr.monomanga

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(
    displayName: String,
    private val vals: Array<Pair<String, String>>,
    defaultState: Int = 0,
) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), defaultState) {
    fun selectedValue() = vals[state].second
}

class GenreFilter :
    SelectFilter(
        "Tür",
        arrayOf(
            "Tüm Türler" to "all",
            "Aksiyon" to "68845665ba4508edd90b6803",
            "Bilim Kurgu" to "68853016cca6c27536e02435",
            "Büyü" to "68845d2fba4508edd90b6a68",
            "Doğaüstü" to "6884586a3f93b2c335a7eb23",
            "Dram" to "6882b538a5832fa7b6621b33",
            "Ecchi" to "46a064824d4b459e9f424b9dcacd4e61",
            "Fantazi" to "6884575fa10f8fc8413aa59c",
            "Gerilim" to "6884edc3188dc5d95677291a",
            "Gizem" to "6884505d3f93b2c335a7ea7e",
            "Harem" to "68845af9ba4508edd90b69c3",
            "Isekai" to "68845d33e6987d135d824dbf",
            "Josei" to "ead89dbbc1f54afd8f24deb6ef5ce0d1",
            "Komedi" to "6883b2c8cb54dd70c707be1f",
            "Korku" to "68845e89e6987d135d824e62",
            "Macera" to "68845758a10f8fc8413aa599",
            "Okul Hayatı" to "6883b2d4cb54dd70c707be25",
            "One-Shot" to "68845446ba4508edd90b67b2",
            "Psikoloji" to "6882b533a5832fa7b6621b30",
            "Romantizm" to "68844e5e3f93b2c335a7ea40",
            "Seinen" to "6882b53da5832fa7b6621b36",
            "Shoujo" to "68844f38ba4508edd90b675e",
            "Shounen" to "688451d63f93b2c335a7eaa6",
            "Spor" to "6883b2cecb54dd70c707be22",
            "Tarihi" to "6884566bba4508edd90b6806",
            "Trajedi" to "68845e84e6987d135d824e5f",
            "Yaşamdan Kesitler" to "6883b2dacb54dd70c707be28",
            "Çıplaklık" to "68844e4aba4508edd90b6750",
        ),
    )

class StatusFilter :
    SelectFilter(
        "Durum",
        arrayOf(
            "Tüm Durumlar" to "all",
            "Devam Ediyor" to "ongoing",
            "Tamamlandı" to "completed",
            "Bırakıldı" to "dropped",
            "Ara Verildi" to "hiatus",
        ),
    )

class TypeFilter :
    SelectFilter(
        "Tip",
        arrayOf(
            "Tüm Tipler" to "all",
            "Manga" to "manga",
            "Webtoon" to "webtoon",
        ),
    )

class SortFilter :
    SelectFilter(
        "Sırala",
        arrayOf(
            "En Yeni" to "newest",
            "En Eski" to "oldest",
            "A-Z" to "name_asc",
            "Z-A" to "name_desc",
            "En Çok Bölüm" to "most_chapters",
        ),
    )
