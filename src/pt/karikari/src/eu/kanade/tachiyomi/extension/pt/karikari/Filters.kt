package eu.kanade.tachiyomi.extension.pt.karikari

import eu.kanade.tachiyomi.source.model.Filter
import okhttp3.HttpUrl

interface UrlFilter {
    fun addTo(url: HttpUrl.Builder)
}

class SortFilter : Filter.Select<String>("Ordem", SORTS.map { it.first }.toTypedArray()) {
    val order get() = SORTS[state].second
}

class GenreFilter :
    Filter.Select<String>("Gênero", GENRES.map { it.first }.toTypedArray()),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        val id = GENRES[state].second ?: return
        url.addQueryParameter("or", "(genero_principal_id.eq.$id,genero_secundario_id.eq.$id)")
    }
}

class ValuesCheckBox(name: String, val values: List<String>) : Filter.CheckBox(name)

open class ValuesGroupFilter(name: String, private val column: String, options: List<ValuesCheckBox>) :
    Filter.Group<ValuesCheckBox>(name, options),
    UrlFilter {
    override fun addTo(url: HttpUrl.Builder) {
        val values = state.filter { it.state }.flatMap { it.values }
        if (values.isEmpty()) return
        url.addQueryParameter(column, values.joinToString(",", prefix = "in.(", postfix = ")") { "\"$it\"" })
    }
}

class StatusFilter :
    ValuesGroupFilter(
        "Situação",
        "status",
        listOf("Em produção", "Finalizado", "Hiato", "Cancelado").map { ValuesCheckBox(it, listOf(it)) },
    )

class AgeRatingFilter :
    ValuesGroupFilter(
        "Faixa etária",
        "classificacao_etaria",
        listOf(
            ValuesCheckBox("Livre", listOf("L", "6")),
            ValuesCheckBox("10+", listOf("10")),
            ValuesCheckBox("12+", listOf("12", "14")),
            ValuesCheckBox("16+", listOf("16")),
            ValuesCheckBox("+18", listOf("18+")),
        ),
    )

private val SORTS = listOf(
    "Mais vistas" to "visualizacoes.desc",
    "Atualizadas recentemente" to "atualizado_em.desc.nullslast",
    "Mais recentes" to "created_at.desc",
)

private val GENRES = listOf(
    "Todos" to null,
    "Ação" to "27d39dba-cbd1-4a0b-a9f5-ad66464aa688",
    "Animais" to "6c73752a-65ce-404d-b72a-0b6734f854ea",
    "Aventura" to "dd486673-dfaa-458e-8f6e-05f79e58b4f0",
    "BL" to "923fc739-a4da-4d12-b6c4-fa94b88b9336",
    "Comédia" to "5f28faaa-8f1e-4950-ae6f-f66bed07b94c",
    "Drama" to "d700ef6d-b39d-4a5c-b070-8e512bc663a3",
    "Esportes" to "6f6d7546-6f67-4879-aa8e-6d8a100f73c9",
    "Fantasia" to "7b2724bb-f9dc-4b32-ae65-44bef994166f",
    "Fantasia Sombria" to "06c81deb-02c8-4962-9f8c-94e8a9cf77fd",
    "Ficção científica" to "6067793d-ffe2-45d2-909c-04143005daee",
    "GL" to "9d2bbc16-4508-4761-bbc4-f976c743a141",
    "Histórico" to "b4f6aec9-c768-4823-8693-b3881c25b334",
    "Jogos" to "834cdc1a-f908-44c3-b78a-ffcf8912c081",
    "LGBTQIA+" to "ca0c96e3-ff11-46c9-adfb-2d9c8aea4f36",
    "Mistério" to "40dd4f88-7636-4a16-b6ad-a3f716c05fa2",
    "Romance" to "f5a6c6e2-e0a4-4134-b147-39e3a27ef91b",
    "Slice of Life" to "a64d0065-435b-423e-bec3-c423153d6e2a",
    "Sobrenatural" to "9c5fa1c3-97fa-471d-86cd-b70cb5d493a2",
    "Super-herói" to "809efb81-cead-42f9-9d9b-78431198ab86",
    "Suspense" to "14ba772c-dcca-48ec-81da-2663172bbde8",
    "Terror" to "745ea922-bb49-424a-b3d1-d51568d81f7e",
    "Vida cotidiana" to "2c4f3544-ed2f-4063-9803-b60f803ee567",
    "Vingança" to "3788f1d8-3509-4565-be3f-a531e5d95480",
    "Zumbis" to "75d4fe7a-ce88-4c65-a588-c1d7a087b49d",
)
