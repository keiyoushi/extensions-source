package eu.kanade.tachiyomi.multisrc.heancms

class HeanCmsIntl(lang: String) {

    val availableLang: String = if (lang in AVAILABLE_LANGS) lang else ENGLISH

    val genreFilterTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Gêneros"
        SPANISH -> "Géneros"
        else -> "Genres"
    }

    val statusFilterTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Estado"
        SPANISH -> "Estado"
        else -> "Status"
    }

    val statusAll: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Todos"
        SPANISH -> "Todos"
        else -> "All"
    }

    val statusOngoing: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Em andamento"
        SPANISH -> "En curso"
        else -> "Ongoing"
    }

    val statusOnHiatus: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Em hiato"
        SPANISH -> "En hiatus"
        else -> "On Hiatus"
    }

    val statusDropped: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Cancelada"
        SPANISH -> "Abandonada"
        else -> "Dropped"
    }

    val sortByFilterTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Ordenar por"
        SPANISH -> "Ordenar por"
        else -> "Sort by"
    }

    val sortByTitle: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Título"
        SPANISH -> "Titulo"
        else -> "Title"
    }

    val sortByViews: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Visualizações"
        SPANISH -> "Número de vistas"
        else -> "Views"
    }

    val sortByLatest: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Recentes"
        SPANISH -> "Recientes"
        else -> "Latest"
    }

    val sortByCreatedAt: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Data de criação"
        SPANISH -> "Fecha de creación"
        else -> "Created at"
    }

    val filterWarning: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Os filtros serão ignorados se a busca não estiver vazia."
        SPANISH -> "Los filtros serán ignorados si la búsqueda no está vacía."
        else -> "Filters will be ignored if the search is not empty."
    }

    val prefShowPaidChapterTitle: String = when (availableLang) {
        SPANISH -> "Mostrar capítulos de pago"
        else -> "Display paid chapters"
    }

    val prefShowPaidChapterSummaryOn: String = when (availableLang) {
        SPANISH -> "Se mostrarán capítulos de pago. Deberá iniciar sesión"
        else -> "Paid chapters will appear. A login might be needed!"
    }

    val prefShowPaidChapterSummaryOff: String = when (availableLang) {
        SPANISH -> "Solo se mostrarán los capítulos gratuitos"
        else -> "Only free chapters will be displayed."
    }

    val paidChapterError: String = when (availableLang) {
        SPANISH -> "Capítulo no disponible. Debe iniciar sesión en Webview y tener el capítulo comprado."
        else -> "Paid chapter unavailable.\nA login/purchase might be needed (using webview)."
    }

    fun urlChangedError(sourceName: String): String = when (availableLang) {
        BRAZILIAN_PORTUGUESE ->
            "A URL da série mudou. Migre de $sourceName " +
                "para $sourceName para atualizar a URL."
        SPANISH ->
            "La URL de la serie ha cambiado. Migre de $sourceName a " +
                "$sourceName para actualizar la URL."
        else ->
            "The URL of the series has changed. Migrate from $sourceName " +
                "to $sourceName to update the URL."
    }

    val idNotFoundError: String = when (availableLang) {
        BRAZILIAN_PORTUGUESE -> "Falha ao obter o ID do slug: "
        SPANISH -> "No se pudo encontrar el ID para: "
        else -> "Failed to get the ID for slug: "
    }

    companion object {
        const val BRAZILIAN_PORTUGUESE = "pt-BR"
        const val ENGLISH = "en"
        const val SPANISH = "es"

        val AVAILABLE_LANGS = arrayOf(BRAZILIAN_PORTUGUESE, ENGLISH, SPANISH)
    }
}
