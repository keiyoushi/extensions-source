package eu.kanade.tachiyomi.extension.pt.taimumangas

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

open class SelectFilter(
    name: String,
    val queryName: String,
    private val options: Array<Pair<String, String>>,
    state: Int = 0,
) : Filter.Select<String>(name, options.map { it.first }.toTypedArray(), state) {
    fun selectedValue(): String = options.getOrNull(state)?.second.orEmpty()
}

class StatusFilter :
    SelectFilter(
        "Status",
        "status",
        arrayOf(
            Pair("Todos os status", ""),
            Pair("Em Andamento", "ongoing"),
            Pair("Finalizada", "completed"),
            Pair("Hiato", "hiatus"),
            Pair("Cancelada", "canceled"),
            Pair("Abandonada", "dropped"),
        ),
    )

class SortFilter :
    SelectFilter(
        "Ordenar por",
        "sort_by",
        arrayOf(
            Pair("Nome", "name"),
            Pair("Data de Criação", "created_at"),
            Pair("Avaliação", "rating"),
            Pair("Visualizações", "total_views"),
        ),
        state = 1,
    )

class SortOrderFilter :
    SelectFilter(
        "Ordem",
        "sort_order",
        arrayOf(
            Pair("Decrescente", "desc"),
            Pair("Crescente", "asc"),
        ),
    )

class GenreCheckBox(name: String, val id: String) : Filter.TriState(name)

class GenreFilter :
    Filter.Group<GenreCheckBox>(
        "Gêneros",
        listOf(
            Pair("Ação", "e954f1f9-393e-4162-af7b-5e388170148b"),
            Pair("Aventura", "0bed9640-7a10-4500-83fe-494a94ed3ea3"),
            Pair("Artes Marciais", "f5fdcfca-a61e-4873-8e7f-5667c15626e6"),
            Pair("Fantasia", "177e4ead-aa20-44f5-9e39-88bce388a417"),
            Pair("Superpoderes", "b207936e-f92e-4a41-b65e-9824f086e36a"),
            Pair("Sobrenatural", "53161d76-1a48-4f56-a058-7aacb87e32e8"),
            Pair("Sci-Fi", "33eaf086-93e8-4cd5-a3e3-77cf23199687"),
            Pair("Apocalíptico", "5c4019b2-1768-4c23-a3ee-76ac42a0a71b"),
            Pair("Guerra", "c511d329-0f2f-4a4e-afde-59a2ba4d9a7d"),
            Pair("Reencarnação", "d2f22988-9df9-47a5-b7c6-7467aa476de6"),
            Pair("Isekai", "875e5a87-b7a6-4756-9a1d-c0cbffac3ffc"),
            Pair("Cultivação", "011b2218-d2cf-4e7a-b7f4-8844f786d420"),
            Pair("Magia", "4654f65b-5836-47c3-b534-ed1d7b97e38d"),
            Pair("Histórico", "e4ecea1c-4b52-4926-8712-b93b18912ced"),
            Pair("Drama", "1f22c2f9-2a01-49b7-87e3-bbbca158ef87"),
            Pair("Tragédia", "e37d3738-428e-4280-8675-cac70e62f516"),
            Pair("Romance", "482278f7-7b97-4089-acc8-ddffc039d946"),
            Pair("Slice of Life", "93b6caea-b6e5-442a-a601-e701a413f00b"),
            Pair("Psicologia", "d11ff009-f781-40b4-9e3f-7f48e0f16149"),
            Pair("Mistério", "73f4bbd5-12d8-466d-bb3e-8358ab5bd339"),
            Pair("Thriller", "fb05bac2-d4d3-4bea-9696-2b60daf91f7a"),
            Pair("Suspense", "1e327270-55dc-4a07-bfd7-3cdbed93a91a"),
            Pair("Crime / Policial", "07dfde02-68ee-4c06-92a3-6e76d9ef8374"),
            Pair("Comédia", "9878e6ef-2e46-456a-aec0-3457a959211c"),
            Pair("Escolar", "2617a39a-7771-47c6-b17c-a7faed59202a"),
            Pair("Shoujo", "a66ab027-8d20-4c19-be07-8f004ff291a4"),
            Pair("Shounen", "deffdc5c-a02f-40f9-8d86-02f426e9458f"),
            Pair("Seinen", "c4683aa8-0ee9-4042-988b-93b2354ce468"),
            Pair("Terror", "299707e4-dbf4-4bab-b346-31f3c802f00a"),
            Pair("Gore", "f52e876a-c624-46ba-a633-c0413344bcf1"),
            Pair("Adulto", "ebadb4a7-946b-43fe-a226-cda49ceab990"),
            Pair("Máfia", "514a0d89-5da5-45d1-ba2b-15c5600532c7"),
            Pair("Sobrevivência", "da9bbbfb-f83d-4e59-bf1e-e9e6f0abf250"),
            Pair("Yuri", "84c7c9e9-15aa-4c58-8542-7b1395ae8905"),
            Pair("Yaoi", "c26631ee-1d73-464f-9b7f-66eb38293e07"),
            Pair("Harém", "84154052-78a6-4e88-955c-8c19e69003db"),
            Pair("Josei", "197a2ff5-fb44-4b1e-8a62-3b765a3d78ba"),
            Pair("Estratégia", "ff141f84-97b7-436d-bb65-3cdd10ce550c"),
            Pair("Cultivo", "140639fb-9439-4ae5-b1a9-b86dee5f15d3"),
            Pair("Murim", "9d66670d-af52-4a30-8082-bdac41735d86"),
            Pair("Culinária", "95a0afd5-37ee-402d-8074-bfc38b85e201"),
            Pair("Esportes", "1ac02b9f-55bc-4e1a-998d-84bc4bd4c604"),
            Pair("Idol", "accb87d7-6912-408e-a280-e0a8d0ebfeab"),
            Pair("Música", "ab16a24a-3efc-4fd7-8801-10b9c0e1a1ce"),
            Pair("Vingança", "d9911173-fe5d-472e-b1ff-37177a838e02"),
        ).map { (name, id) -> GenreCheckBox(name, id) },
    ) {
    fun includedGenreIds(): List<String> = state
        .filter { it.state == Filter.TriState.STATE_INCLUDE }
        .map { it.id }

    fun excludedGenreIds(): List<String> = state
        .filter { it.state == Filter.TriState.STATE_EXCLUDE }
        .map { it.id }
}

fun getFilters(): FilterList = FilterList(
    StatusFilter(),
    SortFilter(),
    SortOrderFilter(),
    Filter.Separator(),
    GenreFilter(),
)
