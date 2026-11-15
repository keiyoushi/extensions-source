package eu.kanade.tachiyomi.extension.tr.otakuangel

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import java.time.Year

class TriStateOption(name: String, val value: String) : Filter.TriState(name)

open class QueryFilter(name: String, val param: String, options: List<Pair<String, String>>) : Filter.Group<TriStateOption>(name, options.map { TriStateOption(it.first, it.second) }) {

    // The site sends the included values, then "|" and the excluded ones
    fun toQuery(): String? {
        val included = state.filter { it.isIncluded() }.map { it.value }
        val excluded = state.filter { it.isExcluded() }.map { it.value }
        if (included.isEmpty() && excluded.isEmpty()) return null
        return included.joinToString(",") + if (excluded.isEmpty()) "" else excluded.joinToString(",", prefix = "|")
    }
}

class TagFilter : QueryFilter("Etiketler", "kategori", TAGS.map { it to it })

class StatusFilter :
    QueryFilter(
        "Durum",
        "durum",
        listOf(
            "Devam Ediyor" to "ongoing",
            "Tamamlandı" to "completed",
            "Ara Verildi" to "hiatus",
            "İptal Edildi" to "cancelled",
        ),
    )

class YearFilter : QueryFilter("Yayın Yılı", "yil", (Year.now().value downTo 1990).map { it.toString() to it.toString() })

fun getFilters() = FilterList(
    Filter.Header("Seçilen etiketlerin hepsi aranır; durumlardan ve yıllardan biri yeterlidir."),
    TagFilter(),
    StatusFilter(),
    YearFilter(),
)

private val TAGS = listOf(
    "Aksiyon", "Brocon", "Bilim Kurgu", "Dövüş Sanatları", "Dram", "Doğaüstü", "Ecchi", "Fantastik", "Game",
    "Gerilim", "Gizem", "Gyaru", "Harem", "Hayalet", "Karanlık Fantazi", "Komedi", "Korku", "Loli", "Shotacon",
    "Macera", "Mecha", "Ninja", "Parodi", "Polisiye", "Psikoloji", "Romantizm", "Savaş", "Büyü", "Sihirli Kız",
    "Siscon", "Spor", "Süper Güçler", "Süper Kahramanlar", "Şeytan", "Trajedi", "Ters harem", "Uzaylı", "Western",
    "Wuxia", "Xianxia", "Xuanhuan", "Vampir", "Villianes", "Yaşamdan Kesitler", "Yaratık", "Zaman Yolculuğu",
    "Zombi", "4-Koma", "Doujinshi", "Game Adaptation", "Manga", "Manhua", "Manhwa", "Oneshot", "Webtoon", "Askeri", "Felsefi", "Hayatta Kalma", "Hayvanlar", "İnanç", "İntikam", "Isekai", "Mahkeme",
    "Dedektiflik", "Mitoloji", "Modern", "Orta Çağ", "Müzik", "Okul Hayatı", "Kıyamet Sonrası", "Köy",
    "Reenkarnasyon", "Sanat", "Suç", "Mafya", "Tarihsel", "Teknoloji", "Tıp", "VR / Oyun İçi Dünya", "Uzay", "Yemek",
    "İnsan dışı", "Josei", "Kodomo", "Seinen", "Shoujo", "Shounen", "Yaoi", "Yuri", "Bara", "Futa", "Şiddet",
    "Aşırı Şiddet", "Crossdressing", "Gender Bender", "Hentai", "Kan", "NTR", "Netori", "Smut", "Tentacle", "Trap",
    "Tsundere", "Yandere", "Yetişkin", "AI Çeviri",
)
