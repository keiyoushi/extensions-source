package eu.kanade.tachiyomi.multisrc.zeistmanga

class ZeistMangaIntl(lang: String) {

    val availableLang: String = if (lang in AVAILABLE_LANGS) lang else ENGLISH

    // Status Filter

    val statusFilterTitle: String = when (availableLang) {
        SPANISH -> "Estado"
        ARAB -> "حالة"
        INDONESIAN -> "Status"
        else -> "Status"
    }

    val statusOngoing: String = when (availableLang) {
        SPANISH -> "En curso"
        ARAB -> "جاري التنفيذ"
        INDONESIAN -> "Sedang berlangsung"
        else -> "Ongoing"
    }

    val statusCompleted: String = when (availableLang) {
        SPANISH -> "Completado"
        ARAB -> "مكتمل"
        INDONESIAN -> "Lengkap"
        else -> "Completed"
    }

    val statusDropped: String = when (availableLang) {
        SPANISH -> "Abandonado"
        ARAB -> "إسقاط"
        INDONESIAN -> "Menjatuhkan"
        else -> "Dropped"
    }

    val statusUpcoming: String = when (availableLang) {
        SPANISH -> "Próximos"
        ARAB -> "القادمة"
        INDONESIAN -> "Mendatang"
        else -> "Upcoming"
    }

    val statusHiatus: String = when (availableLang) {
        SPANISH -> "En hiatus"
        ARAB -> "فجوة"
        INDONESIAN -> "Hiatus"
        else -> "Hiatus"
    }

    val statusCancelled: String = when (availableLang) {
        SPANISH -> "Cancelado"
        ARAB -> "ألغيت"
        INDONESIAN -> "Dibatalkan"
        else -> "Cancelled"
    }

    // Type Filter

    val typeFilterTitle: String = when (availableLang) {
        SPANISH -> "Tipo"
        ARAB -> "يكتب"
        INDONESIAN -> "Jenis"
        else -> "Type"
    }

    val typeManga: String = when (availableLang) {
        SPANISH -> "Manga"
        else -> "Manga"
    }

    val typeManhua: String = when (availableLang) {
        SPANISH -> "Manhua"
        else -> "Manhua"
    }

    val typeManhwa: String = when (availableLang) {
        SPANISH -> "Manhwa"
        else -> "Manhwa"
    }

    val typeNovel: String = when (availableLang) {
        SPANISH -> "Novela"
        else -> "Novel"
    }

    val typeWebNovelJP: String = when (availableLang) {
        SPANISH -> "Web Novel (JP)"
        else -> "Web Novel (JP)"
    }

    val typeWebNovelKR: String = when (availableLang) {
        SPANISH -> "Web Novel (KR)"
        else -> "Web Novel (KR)"
    }

    val typeWebNovelCN: String = when (availableLang) {
        SPANISH -> "Web Novel (CN)"
        else -> "Web Novel (CN)"
    }

    val typeDoujinshi: String = when (availableLang) {
        SPANISH -> "Doujinshi"
        else -> "Doujinshi"
    }

    // Language Filter

    val languageFilterTitle: String = when (availableLang) {
        SPANISH -> "Idioma"
        ARAB -> "لغة"
        INDONESIAN -> "Bahasa"
        else -> "Language"
    }

    // Genre Filter

    val genreFilterTitle: String = when (availableLang) {
        SPANISH -> "Género"
        ARAB -> "جينيرو"
        INDONESIAN -> "Genre"
        else -> "Genre"
    }

    // Extra
    val filterWarning: String = when (availableLang) {
        SPANISH -> "Los filtros serán ignorados si la búsqueda no está vacía."
        ARAB -> "سيتم تجاهل عوامل التصفية إذا لم يكن البحث فارغًا"
        INDONESIAN -> "Filter akan diabaikan jika pencarian tidak kosong."
        else -> "Filters will be ignored if the search is not empty."
    }

    val all: String = when (availableLang) {
        SPANISH -> "Todos"
        ARAB -> "الجميع"
        INDONESIAN -> "Semua"
        else -> "All"
    }

    companion object {
        const val ENGLISH = "en"
        const val SPANISH = "es"
        const val ARAB = "ar"
        const val INDONESIAN = "id"

        val AVAILABLE_LANGS = arrayOf(ENGLISH, SPANISH, ARAB, INDONESIAN)
    }
}
