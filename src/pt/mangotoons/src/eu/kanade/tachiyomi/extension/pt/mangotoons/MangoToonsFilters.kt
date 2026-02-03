package eu.kanade.tachiyomi.extension.pt.mangotoons

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlQueryFilter {
    fun addQueryParameter(url: HttpUrl.Builder)
}

class StatusFilter :
    Filter.Select<String>("Status", statusPairs.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun addQueryParameter(url: HttpUrl.Builder) {
        val value = statusPairs[state].second
        if (value.isNotEmpty()) {
            url.addQueryParameter("status_id", value)
        }
    }

    companion object {
        private val statusPairs = arrayOf(
            Pair("Todos", ""),
            Pair("Ativo", "6"),
            Pair("Cancelado", "4"),
            Pair("Concluído", "3"),
            Pair("Em Andamento", "1"),
            Pair("Hiato", "5"),
            Pair("Pausado", "2"),
        )
    }
}

class FormatFilter :
    Filter.Select<String>("Formato", formatPairs.map { it.first }.toTypedArray()),
    UrlQueryFilter {
    override fun addQueryParameter(url: HttpUrl.Builder) {
        val value = formatPairs[state].second
        if (value.isNotEmpty()) {
            url.addQueryParameter("formato_id", value)
        }
    }

    companion object {
        private val formatPairs = arrayOf(
            Pair("Todos", ""),
            Pair("Comic", "20"),
            Pair("Hentai", "23"),
            Pair("Manga", "24"),
            Pair("Manhwa", "25"),
            Pair("Novel", "18"),
            Pair("Shoujo", "19"),
            Pair("Webtoon", "26"),
            Pair("Yaoi", "21"),
            Pair("Yuri", "22"),
        )
    }
}

class TagCheckBox(name: String, val id: String) : Filter.CheckBox(name)

class TagFilter :
    Filter.Group<TagCheckBox>("Tags", tagsList),
    UrlQueryFilter {
    override fun addQueryParameter(url: HttpUrl.Builder) {
        val tags = state.filter { it.state }.joinToString(",") { it.id }
        if (tags.isNotEmpty()) {
            url.addQueryParameter("tag_ids", tags)
        }
    }

    companion object {
        private val tagsList = listOf(
            TagCheckBox("+18", "48"),
            TagCheckBox("Ação", "2"),
            TagCheckBox("Adulto", "64"),
            TagCheckBox("Apocalipse", "33"),
            TagCheckBox("Apocalíptico", "58"),
            TagCheckBox("Artes Marciais", "24"),
            TagCheckBox("Aventura", "3"),
            TagCheckBox("Bullying", "60"),
            TagCheckBox("Comédia", "6"),
            TagCheckBox("Crime", "51"),
            TagCheckBox("Culinaria", "28"),
            TagCheckBox("Cultivo", "23"),
            TagCheckBox("Demônios", "39"),
            TagCheckBox("Drama", "7"),
            TagCheckBox("Dungeon", "25"),
            TagCheckBox("Ecchi", "42"),
            TagCheckBox("Escolar", "56"),
            TagCheckBox("Esportes", "38"),
            TagCheckBox("Fantasia", "4"),
            TagCheckBox("Fatia da Vida/Slice of Life", "41"),
            TagCheckBox("Ficção Científica", "40"),
            TagCheckBox("Finalizado", "55"),
            TagCheckBox("Gore", "59"),
            TagCheckBox("Harém", "44"),
            TagCheckBox("Histórico", "11"),
            TagCheckBox("Horror", "9"),
            TagCheckBox("Isekai", "19"),
            TagCheckBox("Jogo", "46"),
            TagCheckBox("Josei", "17"),
            TagCheckBox("Luta", "35"),
            TagCheckBox("máfia", "36"),
            TagCheckBox("Magia", "29"),
            TagCheckBox("manhua", "45"),
            TagCheckBox("Militar", "14"),
            TagCheckBox("Mistério", "43"),
            TagCheckBox("Moderno", "54"),
            TagCheckBox("Monstros", "37"),
            TagCheckBox("Murim", "31"),
            TagCheckBox("Necromante", "32"),
            TagCheckBox("One-shot", "18"),
            TagCheckBox("Oneshot", "49"),
            TagCheckBox("Policial", "52"),
            TagCheckBox("Psicológico", "27"),
            TagCheckBox("Reencarnação", "21"),
            TagCheckBox("Regressão", "47"),
            TagCheckBox("Retorno", "20"),
            TagCheckBox("Romance", "5"),
            TagCheckBox("Sci-Fi", "62"),
            TagCheckBox("Seinen", "34"),
            TagCheckBox("Shoujo", "16"),
            TagCheckBox("Shounen", "15"),
            TagCheckBox("Shounen Ai", "63"),
            TagCheckBox("Sistema", "22"),
            TagCheckBox("Slice of Life", "61"),
            TagCheckBox("Sobrenatural", "13"),
            TagCheckBox("SuperPoder", "30"),
            TagCheckBox("Super Poderes", "57"),
            TagCheckBox("Suspense", "10"),
            TagCheckBox("Terror", "8"),
            TagCheckBox("Tragédia", "26"),
            TagCheckBox("Viagem no Tempo", "53"),
            TagCheckBox("Vida escolar", "12"),
            TagCheckBox("Yuri", "50"),
        )
    }
}
