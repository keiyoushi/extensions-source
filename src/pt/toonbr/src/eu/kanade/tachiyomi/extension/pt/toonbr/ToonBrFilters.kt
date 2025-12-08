package eu.kanade.tachiyomi.extension.pt.toonbr

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun getFilters() = FilterList(
    CategoryFilter("Categoria", getCategories()),
)

class CategoryFilter(displayName: String, private val categories: List<Pair<String, String>>) :
    Filter.Select<String>(displayName, categories.map { it.first }.toTypedArray()) {
    val selected: String?
        get() = if (state == 0) null else categories.getOrNull(state)?.second
}

private fun getCategories() = listOf(
    Pair("Todas", ""),
    Pair("Ação", "9c302e17-a1df-42f7-84f9-8de76bc97afb"),
    Pair("Artes Marciais", "016c5ed9-8471-4a53-ad4e-22108ddc7cef"),
    Pair("Aventura", "02aa6ee2-1862-4044-a81c-7417b588d096"),
    Pair("Comédia", "148ba9b3-1353-430e-ab3d-e845cfb35a77"),
    Pair("Cultivo", "541d75e5-233d-42f7-8a9e-77f3f0674c25"),
    Pair("Drama", "e36a8377-378f-472c-a622-a36ed0de493a"),
    Pair("Dungeons", "c6f1d753-cfd4-4e5c-bbfa-45a742d416c8"),
    Pair("Fantasia", "152d8043-499f-46af-bcb1-fa85d4609b06"),
    Pair("Ficção Científica", "15b9f655-752a-43d2-be81-fcade4b88600"),
    Pair("Garota Monstro", "8881c693-f64d-4c7c-854e-d36d766e4c94"),
    Pair("Guerra", "276ad40f-6a47-4e95-86b5-edd05a13758e"),
    Pair("Harém", "804d6052-4b08-4690-b068-bb0c419bd3e7"),
    Pair("Histórico", "a25e9dc7-4542-4e12-bc62-13b26012f817"),
    Pair("Isekai", "7fc49228-f081-4829-bae0-124c72b6d3b1"),
    Pair("Josei", "56d5c790-8cdb-4856-a7c0-a05d9f9ac27f"),
    Pair("Magia", "bcf9fbae-b594-45ef-88d3-2238e6e69d0b"),
    Pair("Manhua", "630647d8-0644-479d-bb4f-dd93bdc2f260"),
    Pair("Mistério", "78285b9f-1ffa-4f8e-a364-1f5c474e0c42"),
    Pair("Murim", "f023cc3a-e739-434f-b9a7-ebdb6820012e"),
    Pair("Novel", "36737e8e-bd53-49c6-9778-b068a7e1af33"),
    Pair("Overpower", "b7d6eaa1-9f63-4954-9337-7d3ff8db28da"),
    Pair("Poderes", "798e2caa-b993-4d02-82f6-9ca1670b6f9c"),
    Pair("Psicológico", "aab7e8c0-9947-4940-9df1-66fab2cfa9e0"),
    Pair("Reencarnação", "015f5641-f9ab-49b5-8e40-7e3ab11d6841"),
    Pair("Regressão", "378bf675-df29-4e83-969c-baa9cb704ebe"),
    Pair("Retorno", "d551ebe5-18bc-4e43-b8dc-b8cc0ca338e9"),
    Pair("Romance", "1572cea3-6b8d-4384-869e-98f13eeb0b72"),
    Pair("Seinen", "201ba651-1538-4ba9-8d38-2bf27ef021fa"),
    Pair("Shoujo", "b555b360-813b-4996-886c-f6c2b18f1627"),
    Pair("Shounen", "0c496918-8310-4faa-a578-5146869beaf1"),
    Pair("Sistema", "9b34cfee-ab0f-4111-b65f-f124f1efd4e5"),
    Pair("Slice of Life", "9b4eac41-405d-4add-92bb-ca4de396d06e"),
    Pair("Sobrenatural", "4640673a-1697-4a1b-bc0f-449b85088639"),
    Pair("Superpoderes", "575d014e-fb7a-43fc-9f9d-883fdd789c90"),
    Pair("Terror", "b67c12d8-89ae-491a-b902-d616c6d52c49"),
    Pair("Torre", "a8beff9f-bc9f-45f7-b733-487d7365b153"),
    Pair("Transformação", "392a32a3-0bef-4f97-bb2f-5854f1701a21"),
    Pair("Tragédia", "f1189c95-a9ad-43cd-9973-85f1c9000953"),
    Pair("Vilão", "22bce1fa-5c06-45f1-a366-99f4556646fa"),
    Pair("Vingança", "49073ee7-379c-4c59-9379-757b5e9abd32"),
    Pair("Wuxia", "4e7b53c7-cc94-4967-859c-6be94bf3a52e"),
)
