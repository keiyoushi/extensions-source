package eu.kanade.tachiyomi.extension.ar.arabhentai

import eu.kanade.tachiyomi.source.model.Filter

class FilterCheckbox(name: String, val uriPart: String) : Filter.CheckBox(name)

class GenreFilter :
    Filter.Group<FilterCheckbox>(
        "التصنيفات",
        arrayOf(
            "أستاذ",
            "أثداء كبيرة",
            "إذلال",
            "اغتصاب",
            "إينسست",
            "بوسي",
            "جنس بالثدي",
            "جنس جماعي",
            "جنس فموي",
            "حريم",
            "خيال",
            "خيانة",
            "دراما",
            "رومانسي",
            "طالب | طالبة",
            "قوى خارقة",
            "كايروز",
            "مؤخرة كبيرة",
            "مانجا - مانهوا",
            "مانهوا",
            "مبارلة الأجساد",
            "مدرسية",
            "معلم | معلمة",
            "مكان عام",
            "ممرضة",
            "ميلف",
            "نفسي",
            "هنتاي",
            "ياوي",
            "يوري",
        ).map { FilterCheckbox(it, it) },
    )

class StatusFilter :
    Filter.Group<FilterCheckbox>(
        "الحالة",
        listOf(
            FilterCheckbox("مستمر", "0"),
            FilterCheckbox("مكتمل", "1"),
        ),
    )
