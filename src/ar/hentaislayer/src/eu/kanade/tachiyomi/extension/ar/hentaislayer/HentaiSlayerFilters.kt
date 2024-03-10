package eu.kanade.tachiyomi.extension.ar.hentaislayer

import eu.kanade.tachiyomi.source.model.Filter

class StatusFilter : UriPartFilter(
    "الحالة",
    arrayOf(
        Pair("الكل", ""),
        Pair("مستمر", "مستمر"),
        Pair("متوقف", "متوقف"),
        Pair("مكتمل", "مكتمل"),
    ),
)

class TypeFilter : UriPartFilter(
    "النوع",
    arrayOf(
        Pair("الكل", ""),
        Pair("مانجا", "مانجا"),
        Pair("مانهوا", "مانهوا"),
        Pair("كوميكس", "كوميكس"),
    ),
)

private val genres = listOf(
    Genre("أكشن", "أكشن"),
    Genre("ألعاب جنسية", "ألعاب جنسية"),
    Genre("إذلال", "إذلال"),
    Genre("إيلف", "إيلف"),
    Genre("ابتزاز", "ابتزاز"),
    Genre("استعباد", "استعباد"),
    Genre("اغتصاب", "اغتصاب"),
    Genre("بدون حجب", "بدون حجب"),
    Genre("بشرة سمراء", "بشرة سمراء"),
    Genre("تاريخي", "تاريخي"),
    Genre("تحكم بالعقل", "تحكم بالعقل"),
    Genre("تراب", "تراب"),
    Genre("تسوندري", "تسوندري"),
    Genre("تصوير", "تصوير"),
    Genre("جنس بالقدم", "جنس بالقدم"),
    Genre("جنس جماعي", "جنس جماعي"),
    Genre("جنس شرجي", "جنس شرجي"),
    Genre("حريم", "حريم"),
    Genre("حمل", "حمل"),
    Genre("خادمة", "خادمة"),
    Genre("خيال", "خيال"),
    Genre("خيانة", "خيانة"),
    Genre("دراغون بول", "دراغون بول"),
    Genre("دراما", "دراما"),
    Genre("رومانسي", "رومانسي"),
    Genre("سحر", "سحر"),
    Genre("شوتا", "شوتا"),
    Genre("شيطانة", "شيطانة"),
    Genre("شيميل", "شيميل"),
    Genre("طالبة مدرسة", "طالبة مدرسة"),
    Genre("عمة", "عمة"),
    Genre("فوتا", "فوتا"),
    Genre("لولي", "لولي"),
    Genre("محارم", "محارم"),
    Genre("مدرسي", "مدرسي"),
    Genre("مكان عام", "مكان عام"),
    Genre("ملون", "ملون"),
    Genre("ميلف", "ميلف"),
    Genre("ناروتو", "ناروتو"),
    Genre("هجوم العمالقة", "هجوم العمالقة"),
    Genre("ون بيس", "ون بيس"),
    Genre("ياوي", "ياوي"),
    Genre("يوري", "يوري"),
)

class Genre(val name: String, val uriPart: String)

class GenreCheckBox(name: String, val uriPart: String) : Filter.CheckBox(name)

class GenresFilter :
    Filter.Group<GenreCheckBox>("التصنيفات", genres.map { GenreCheckBox(it.name, it.uriPart) })

open class UriPartFilter(displayName: String, private val pairs: Array<Pair<String, String>>) :
    Filter.Select<String>(displayName, pairs.map { it.first }.toTypedArray()) {
    fun toUriPart() = pairs[state].second
}
