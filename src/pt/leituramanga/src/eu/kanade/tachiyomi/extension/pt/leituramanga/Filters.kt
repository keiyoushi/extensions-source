package eu.kanade.tachiyomi.extension.pt.leituramanga

import eu.kanade.tachiyomi.source.model.Filter

class GenreFilter : Filter.Select<String>("Gêneros", genres.map { it.first }.toTypedArray()) {
    val selectedValue: String
        get() = genres[state].second
}

class StatusFilter : Filter.Select<String>("Status", status.map { it.first }.toTypedArray()) {
    val selectedValue: String
        get() = status[state].second
}

class SortFilter : Filter.Select<String>("Ordenar por", sort.map { it.first }.toTypedArray()) {
    val selectedValue: String
        get() = sort[state].second
}

private val genres = listOf(
    Pair("Todos", ""),
    Pair("Romance", "680b9c9f94036afb098c9496"),
    Pair("Fantasia", "680b9c9f94036afb098c9493"),
    Pair("Shoujo", "680b9c3d94036afb098c9464"),
    Pair("Drama", "680b9c3c94036afb098c945e"),
    Pair("Historico", "680b9ca494036afb098c94ab"),
    Pair("Manhwa", "680c51ab3720580a3d51c003"),
    Pair("Comedia", "680b9c9294036afb098c9479"),
    Pair("Josei", "680b9ca494036afb098c94ae"),
    Pair("Ação", "680b9c9294036afb098c9473"),
    Pair("Aventura", "680bbfb23720580a3d519cc2"),
    Pair("Magia", "680ba2e1881ffa93cedbf929"),
    Pair("Sobrenatural", "680b9ca594036afb098c94b8"),
    Pair("Reencarnação", "680b9c3c94036afb098c9461"),
    Pair("Shounen", "680b9c9394036afb098c9480"),
    Pair("Isekai", "680bbb2f612fa8d9c95306f4"),
    Pair("Mistério", "680b9ca594036afb098c94b1"),
    Pair("Artes Marciais", "680b9c9294036afb098c9476"),
    Pair("Familia", "680ba2e1881ffa93cedbf926"),
    Pair("Psicológico", "680be3c93720580a3d51a5dd"),
    Pair("Adulto", "680bb39c01897055e493d58e"),
    Pair("Escolar", "680c582c3720580a3d51c1b3"),
    Pair("Slice of Life", "680bc3393720580a3d519d00"),
    Pair("Seinen", "680bafade71bcb4e5776b62c"),
    Pair("+18", "680bec6f3720580a3d51a83a"),
    Pair("Tragedia", "680bd6443720580a3d51a2ed"),
    Pair("Demonios", "680b9c9394036afb098c947c"),
    Pair("Harem", "680bbc85612fa8d9c9530773"),
    Pair("Vida escolar", "680bc3393720580a3d519d03"),
    Pair("Smut", "680bec2e3720580a3d51a81f"),
    Pair("Shoujo Ai", "680b9ca594036afb098c94b5"),
    Pair("Yaoi", "680d89eb17c65f3600568d03"),
    Pair("Vingança", "680cbfab46c075b3b585d074"),
    Pair("Sobrevivencia", "680c22743720580a3d51b596"),
    Pair("Viagem no tempo", "680c2b353720580a3d51b79f"),
    Pair("Realeza", "680c09953720580a3d51afc3"),
    Pair("Manhua", "680c28ab3720580a3d51b6da"),
    Pair("Completo", "680d9cd817c65f360056a114"),
    Pair("Demônio", "680d1da917c65f36005619b1"),
    Pair("Maduro", "680c19563720580a3d51b312"),
    Pair("Regressão", "680c22743720580a3d51b591"),
    Pair("Adaptação", "680c2da43720580a3d51b81d"),
    Pair("Estratégia", "680e7e8919e61865daa97253"),
    Pair("Ecchi", "680ba5b2e894c590bda862a5"),
    Pair("Harém Reverso", "680d22e317c65f3600561fc6"),
    Pair("Época", "680c4f7e3720580a3d51bf79"),
    Pair("Moderno", "680d30e717c65f3600562eb2"),
    Pair("Sistema", "680e7eb619e61865daa9729a"),
    Pair("Finalizado", "680d939217c65f3600569626"),
    Pair("Webtoon", "680f2ec719e61865daaa3c83"),
    Pair("Vilã", "680c09953720580a3d51afca"),
    Pair("Murim", "680ea72f19e61865daa9a357"),
    Pair("Time Travel", "680c3c453720580a3d51bb25"),
    Pair("Obsceno", "680c19563720580a3d51b315"),
    Pair("Colegial", "680e922319e61865daa989e5"),
    Pair("Sci-Fi", "680da41417c65f360056a73c"),
    Pair("Ficção Científica", "690a159576bc80ff0421a73a"),
    Pair("musical", "680c3cd53720580a3d51bb58"),
    Pair("Gender Bender", "680d81bb17c65f36005684e4"),
    Pair("Apocalipse", "680e810519e61865daa97513"),
    Pair("Horror", "680eaa3e19e61865daa9a898"),
    Pair("Yuri", "680c4e043720580a3d51bf1f"),
    Pair("Esportes", "680d9ba617c65f360056a01d"),
    Pair("Overpowered", "680e7f5e19e61865daa97345"),
    Pair("Jogo", "680d816317c65f360056843c"),
    Pair("Triângulo Amoroso", "680da41517c65f360056a740"),
    Pair("Mulher Overpower", "6910ae7e7351418c61c1513a"),
    Pair("Erótico", "6925bfcbad1e8b3e82d91dd0"),
    Pair("Younger Man", "6925bfcbad1e8b3e82d91dd2"),
    Pair("Cultivo", "680f65aa19e61865daaa56a2"),
    Pair("Gore", "680eaa3e19e61865daa9a895"),
    Pair("Super Poderes", "680e781319e61865daa969ca"),
    Pair("Policial", "680eaa3e19e61865daa9a89c"),
    Pair("Ação Social", "690b64adb7b932cff6fffe68"),
    Pair("Fatia da Vida", "690a2e9576bc80ff044a1a3b"),
    Pair("Terror", "690a2fc776bc80ff044c17a8"),
    Pair("Vida Adulta", "680e008f74096432d115796a"),
    Pair("Older Woman", "6925bfcbad1e8b3e82d91dd4"),
    Pair("Estudante", "680e008e74096432d1157956"),
    Pair("Vampiro", "680d338917c65f36005631fb"),
    Pair("Político", "680df96674096432d11570e5"),
    Pair("Vampiros", "680d645317c65f36005663c5"),
    Pair("Bi", "680da41317c65f360056a731"),
    Pair("Anjo", "680d9d1817c65f360056a146"),
    Pair("NSFW", "680e008e74096432d1157965"),
    Pair("Parte da Vida", "69059cf3843134a734cae835"),
)

private val status = listOf(
    Pair("Todos", ""),
    Pair("Em andamento", "Ongoing"),
    Pair("Completo", "Completed"),
)

private val sort = listOf(
    Pair("Atualização", "time"),
    Pair("Visualizações", "view"),
    Pair("Avaliação", "rate"),
)
