package eu.kanade.tachiyomi.extension.pt.pizzariascan

import eu.kanade.tachiyomi.multisrc.mangawork.MangaWork
import java.text.Normalizer
import java.util.Locale

class Pizzariascan :
    MangaWork(
        name = "PizzariaScan",
        baseUrl = "https://pizzariacomics.com",
        lang = "pt-BR",
    ) {

    override val id: Long = 3359822911747375789

    override val seriesPath = "todas-as-obras"

    override fun getOrderFilterOptions() = PizzariascanFilters.orderFilterOptions

    override fun getStatusFilterOptions() = PizzariascanFilters.statusFilterOptions

    override fun getTypeFilterOptions() = PizzariascanFilters.typeFilterOptions

    override fun getGenreFilterOptions() = PizzariascanFilters.genreFilterOptions

    override fun getYearFilterOptions() = PizzariascanFilters.yearFilterOptions

    override fun parseChapterDate(date: String?): Long {
        val normalizedDate = date
            ?.let(::normalizeDiacritics)
            ?.lowercase(Locale.ROOT)
            ?.split(" de ")
            ?.takeIf { it.size == 3 }
            ?.let { (day, month, year) ->
                val normalizedDay = day.toIntOrNull()?.toString()?.padStart(2, '0') ?: return 0L
                val normalizedMonth = ptBrMonths[month] ?: return 0L
                val normalizedYear = year.toIntOrNull()?.toString() ?: return 0L
                "$normalizedDay/$normalizedMonth/$normalizedYear"
            }
            ?: return 0L

        return super.parseChapterDate(normalizedDate)
    }

    companion object {
        private val diacriticsRegex = "\\p{Mn}+".toRegex()

        private val ptBrMonths = mapOf(
            "janeiro" to "01",
            "fevereiro" to "02",
            "marco" to "03",
            "abril" to "04",
            "maio" to "05",
            "junho" to "06",
            "julho" to "07",
            "agosto" to "08",
            "setembro" to "09",
            "outubro" to "10",
            "novembro" to "11",
            "dezembro" to "12",
        )

        private fun normalizeDiacritics(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(diacriticsRegex, "")
    }
}
