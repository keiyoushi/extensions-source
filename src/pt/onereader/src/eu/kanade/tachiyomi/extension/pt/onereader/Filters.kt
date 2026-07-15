package eu.kanade.tachiyomi.extension.pt.onereader

import eu.kanade.tachiyomi.source.model.Filter

internal open class SelectFilter(
    name: String,
    private val options: Array<Pair<String, String>>,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
    fun selected(): String = options[state].second
}

internal class OrderFilter :
    SelectFilter(
        "Ordenar por",
        arrayOf(
            "A-Z" to "az",
            "Atualizados recentemente" to "recent",
        ),
    )

internal class TypeFilter :
    SelectFilter(
        "Tipo",
        arrayOf(
            "Todos" to "",
            "Adulto" to "Adulto",
            "Mangá" to "Manga",
            "Manhua" to "Manhua",
            "Manhwa" to "Manhwa",
            "Shoujo" to "Shoujo",
            "Webtoon" to "Webtoon",
        ),
    )

internal class StatusFilter :
    SelectFilter(
        "Status",
        arrayOf(
            "Todos" to "",
            "Em lançamento" to "Em Lançamento",
            "Completo" to "Completo",
            "Hiato" to "Hiatus",
            "Cancelado" to "Cancelado",
        ),
    )

internal class TagFilter :
    SelectFilter(
        "Tag",
        arrayOf(
            "Todas" to "",
            "Ação" to "Ação",
            "Adaptação" to "Adaptação",
            "Adulto" to "Adulto",
            "Aliens" to "Aliens",
            "Animais" to "Animais",
            "Artes Marciais" to "Artes Marciais",
            "Aventura" to "Aventura",
            "Boys' Love" to "Boys' Love",
            "Brutalidade" to "Brutalidade",
            "Comédia" to "Comédia",
            "Cosplayer" to "Cosplayer",
            "Crime" to "Crime",
            "Crossdressing" to "Crossdressing",
            "Culinária" to "Culinária",
            "Cultivação" to "Cultivação",
            "Cultivo" to "Cultivo",
            "Delinquentes" to "Delinquentes",
            "Demônios" to "Demônios",
            "Drama" to "Drama",
            "Ecchi" to "Ecchi",
            "Elf" to "Elf",
            "Erótica" to "Erótica",
            "Escolar" to "Escolar",
            "Escritório" to "Escritório",
            "Esporte" to "Esporte",
            "Estratégia" to "Estratégia",
            "Fantasia" to "Fantasia",
            "Fantasmas" to "Fantasmas",
            "Ficção Científica" to "Ficção Científica",
            "Filosófico" to "Filosófico",
            "Garota Mágica" to "Garota Mágica",
            "Garota Monstro" to "Garota Monstro",
            "Gore" to "Gore",
            "Gyaru" to "Gyaru",
            "Harém" to "Harém",
            "Harém Reverso" to "Harém Reverso",
            "Histórico" to "Histórico",
            "Horror" to "Horror",
            "Isekai" to "Isekai",
            "Jogos" to "Jogos",
            "Mafia" to "Mafia",
            "Máfia" to "Máfia",
            "Magia" to "Magia",
            "Mecha" to "Mecha",
            "Medicina" to "Medicina",
            "Médico" to "Médico",
            "Militar" to "Militar",
            "Militares" to "Militares",
            "Mistério" to "Mistério",
            "Monstros" to "Monstros",
            "Murim" to "Murim",
            "Música" to "Música",
            "Necromancer" to "Necromancer",
            "Ninja" to "Ninja",
            "Policial" to "Policial",
            "Pós-Apocalíptico" to "Pós-Apocalíptico",
            "Psicológico" to "Psicológico",
            "Realidade Virtual" to "Realidade Virtual",
            "Reencarnação" to "Reencarnação",
            "Regressão" to "Regressão",
            "Romance" to "Romance",
            "Samurai" to "Samurai",
            "Shounen" to "Shounen",
            "Sistema de Jogo" to "Sistema de Jogo",
            "Sobrenatural" to "Sobrenatural",
            "Sobrevivência" to "Sobrevivência",
            "Super Herói" to "Super Herói",
            "Superação" to "Superação",
            "Suspense" to "Suspense",
            "Terror" to "Terror",
            "Tragédia" to "Tragédia",
            "Traição" to "Traição",
            "Troca de Gênero" to "Troca de Gênero",
            "Vampiro" to "Vampiro",
            "Viagem no Tempo" to "Viagem no Tempo",
            "Vida Cotidiana" to "Vida Cotidiana",
            "Vida Escolar" to "Vida Escolar",
            "Video Games" to "Video Games",
            "Vilã" to "Vilã",
            "Vilão" to "Vilão",
            "Vingança" to "Vingança",
            "Violência" to "Violência",
            "Yaoi" to "Yaoi",
            "Yuri" to "Yuri",
            "Zumbis" to "Zumbis",
        ),
    )
