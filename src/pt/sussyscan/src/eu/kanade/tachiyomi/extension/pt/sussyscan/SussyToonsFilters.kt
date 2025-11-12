package eu.kanade.tachiyomi.extension.pt.sussyscan

import eu.kanade.tachiyomi.source.model.Filter

class OrderByFilter : Filter.Select<String>(
    "Ordenar por",
    arrayOf("Última Atualização", "Lançamentos", "Mais Visualizadas", "Melhor Avaliação", "Nome"),
) {
    val selected get() = ORDER_BY_VALUES[state]

    companion object {
        private val ORDER_BY_VALUES = arrayOf("ultima_atualizacao", "criacao", "visualizacoes_geral", "rating", "nome")
    }
}

class OrderDirectionFilter : Filter.Select<String>(
    "Ordem",
    arrayOf("Decrescente", "Crescente"),
) {
    val selected get() = ORDER_DIRECTION_VALUES[state]

    companion object {
        private val ORDER_DIRECTION_VALUES = arrayOf("DESC", "ASC")
    }
}

class GenreFilter : Filter.Select<String>(
    "Gênero",
    arrayOf("Todos", "Livres", "Shoujo / Romances", "Hentais", "Novel", "Yaoi", "Mangás"),
) {
    val selected get() = GENRE_IDS[state]

    companion object {
        private val GENRE_IDS = arrayOf("", "1", "4", "5", "6", "7", "8")
    }
}

class FormatFilter : Filter.Select<String>(
    "Formato",
    arrayOf("Todos", "Manhwa", "Manhua", "Mangá", "Novel", "Anime"),
) {
    val selected get() = FORMAT_IDS[state]

    companion object {
        private val FORMAT_IDS = arrayOf("", "1", "2", "3", "4", "7")
    }
}

class StatusFilter : Filter.Select<String>(
    "Status",
    arrayOf("Todos", "Em Andamento", "Concluído", "Hiato", "Cancelado"),
) {
    val selected get() = STATUS_IDS[state]

    companion object {
        private val STATUS_IDS = arrayOf("", "1", "2", "3", "4")
    }
}

class TagsFilter(tags: List<Tag>) : Filter.Group<Tag>("Tags", tags)

class Tag(val id: String, name: String) : Filter.CheckBox(name)

val TAGS_LIST = listOf(
    Tag("1", "Ação"),
    Tag("2", "Aventura"),
    Tag("3", "Comédia"),
    Tag("4", "Drama"),
    Tag("5", "Fantasia"),
    Tag("6", "Terror"),
    Tag("7", "Mistério"),
    Tag("8", "Romance"),
    Tag("9", "Sci-Fi"),
    Tag("10", "Slice of Life"),
    Tag("11", "Esportes"),
    Tag("12", "Thriller"),
    Tag("13", "Sobrenatural"),
    Tag("14", "Histórico"),
    Tag("15", "Mecha"),
    Tag("16", "Psicológico"),
    Tag("17", "Seinen"),
    Tag("18", "Shoujo"),
    Tag("19", "Shounen"),
    Tag("20", "Josei"),
    Tag("21", "Isekai"),
    Tag("22", "Artes Marciais"),
    Tag("23", "Gore"),
    Tag("24", "Yuri"),
    Tag("25", "Yaoi"),
    Tag("26", "Escolar"),
    Tag("27", "Animais"),
    Tag("28", "Apocalipse"),
    Tag("29", "Adulto"),
    Tag("30", "Boys"),
    Tag("31", "Bullying"),
    Tag("32", "Construção"),
    Tag("33", "Crime"),
    Tag("34", "Culinária"),
    Tag("35", "Demônios"),
    Tag("36", "Ecchi"),
    Tag("37", "Esporte"),
    Tag("38", "Estratégia"),
    Tag("39", "Família"),
    Tag("40", "Fatos Reais"),
    Tag("41", "Fazenda"),
    Tag("42", "Ficção Científica"),
    Tag("43", "Guerra"),
    Tag("44", "Hárem"),
    Tag("45", "Horror"),
    Tag("46", "Jogo"),
    Tag("47", "Linha do Tempo"),
    Tag("48", "Luta"),
    Tag("49", "Máfia"),
    Tag("50", "Magia"),
    Tag("51", "Monstros"),
    Tag("52", "Murim"),
    Tag("53", "Musculação"),
    Tag("54", "Necromante"),
    Tag("55", "Overpower"),
    Tag("56", "Pets"),
    Tag("57", "Realidade Virtual"),
    Tag("58", "Reencarnação"),
    Tag("59", "Regressão"),
    Tag("60", "Religião"),
    Tag("61", "Sistema"),
    Tag("62", "Super Poderes"),
    Tag("63", "Suspense"),
    Tag("64", "Tela de Sistema"),
    Tag("65", "Tragédias"),
    Tag("66", "Vida Escolar"),
    Tag("67", "Vingança"),
    Tag("68", "Violência"),
    Tag("69", "Volta no Tempo"),
)
