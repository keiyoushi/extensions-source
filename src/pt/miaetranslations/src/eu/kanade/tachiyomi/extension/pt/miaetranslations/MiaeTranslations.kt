package eu.kanade.tachiyomi.extension.pt.miaetranslations

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MiaeTranslations : Madara(
    "Miae Translations",
    "https://miaetranslations.site",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val fetchGenres = false
    override var genresList = listOf(
        Genre("Ação", "acao"),
        Genre("Adaptação", "adaptacao"),
        Genre("Adulto", "adulto"),
        Genre("Amigos de Infância", "amigos-de-infancia"),
        Genre("Angústia", "angustia"),
        Genre("Animais", "animais"),
        Genre("Antologia", "antologia"),
        Genre("Aventura", "aventura"),
        Genre("BDSM", "bdsm"),
        Genre("Casamento Arranjado", "casamento-arranjado"),
        Genre("Comédia", "comedia"),
        Genre("Conto", "conto"),
        Genre("Dark Romance", "dark-romance"),
        Genre("Demônios", "demonios"),
        Genre("Drama", "drama"),
        Genre("Erótico", "erotico"),
        Genre("Escolar", "escolar"),
        Genre("Escritório", "escritorio"),
        Genre("Familia", "familia"),
        Genre("Fantasia", "fantasia"),
        Genre("Ficção Científica", "ficcao-cientifica"),
        Genre("Harém Reverso", "harem-reverso-harem-reverso"),
        Genre("Histórico", "historico"),
        Genre("Josei", "josei"),
        Genre("Magia", "magia"),
        Genre("Médico", "medico"),
        Genre("Mindbreak", "mindbreak"),
        Genre("Mistério", "misterio"),
        Genre("Moderno", "moderno"),
        Genre("Omegaverse", "omegaverse"),
        Genre("Oneshot", "oneshot"),
        Genre("Psicológico", "psicologico"),
        Genre("Redenção", "redencao"),
        Genre("Reencarnação", "reencarnacao"),
        Genre("Romance", "romance"),
        Genre("Sem Censura", "sem-censura"),
        Genre("Shoujo", "shoujo"),
        Genre("Smut", "smut"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Tragédia", "tragedia"),
        Genre("Traição", "traicao"),
        Genre("Transmigração", "transmigracao"),
        Genre("Troca de Corpos", "troca-de-corpos"),
        Genre("Vampiro", "vampiro"),
        Genre("Viagem no Tempo", "viagem-no-tempo"),
        Genre("Vingança", "vinganca"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
}
