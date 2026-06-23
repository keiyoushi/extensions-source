package eu.kanade.tachiyomi.extension.fr.hanabook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CatalogueResponse(
    val success: Boolean = false,
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("nb_pages") val nbPages: Int = 1,
    val licences: List<Licence> = emptyList(),
)

@Serializable
class Licence(
    @SerialName("id_serie") val idSerie: Int,
    @SerialName("titre_serie") val titreSerie: String,
    val seo: String,
    @SerialName("cover_ref") val coverRef: Int? = null,
    @SerialName("nb_volumes") val nbVolumes: Int = 0,
    val genres: List<Genre> = emptyList(),
)

@Serializable
class Genre(
    val id: Int,
    val nom: String,
)

@Serializable
class SearchResponse(
    val success: Boolean = false,
    val ebooks: List<SearchEbook> = emptyList(),
)

@Serializable
class SearchEbook(
    val ref: Int,
    val titre: String,
    @SerialName("id_serie") val idSerie: Int,
    @SerialName("titre_serie") val titreSerie: String,
    val seo: String,
    val auteur: String? = null,
    val genres: List<String> = emptyList(),
    @SerialName("nb_pages") val nbPages: Int = 0,
    @SerialName("num_volume") val numVolume: Int? = null,
    @SerialName("nb_volume_total") val nbVolumeTotal: Int? = null,
)

@Serializable
class DossierResponse(
    val success: Boolean = false,
    val dossier: Dossier? = null,
)

@Serializable
class Dossier(
    @SerialName("id_licence") val idLicence: Int,
    val titre: String,
    @SerialName("category_label") val categoryLabel: String? = null,
    val cover: Cover? = null,
    val characters: List<Character> = emptyList(),
    val dossier: DossierContent? = null,
)

@Serializable
class Cover(
    val jpg: String? = null,
    val webp: String? = null,
)

@Serializable
class Character(
    val name: String,
    val role: String? = null,
)

@Serializable
class DossierContent(
    val auteurs: String? = null,
    val publication: String? = null,
    val reception: String? = null,
    val adaptations: String? = null,
)

@Serializable
class ProduitResponse(
    val success: Boolean = false,
    val ebook: Ebook? = null,
)

@Serializable
class EbookMetaResponse(
    val success: Boolean = false,
    val ebook: EbookMeta? = null,
    val message: String? = null,
)

@Serializable
class EbookMeta(
    @SerialName("ref_book") val refBook: Int,
    @SerialName("titre_book") val titreBook: String,
    val seo: String? = null,
    val demo: Int = 0,
    @SerialName("max_page") val maxPage: Int = 0,
    @SerialName("ebook_abo") val ebookAbo: Int = 0,
)

@Serializable
class Ebook(
    val ref: Int,
    val titre: String,
    @SerialName("id_serie") val idSerie: Int = 0,
    @SerialName("titre_serie") val titreSerie: String = "",
    val description: String? = null,
    val auteur: String? = null,
    @SerialName("num_volume") val numVolume: Int? = null,
    @SerialName("nb_volume_total") val nbVolumeTotal: Int? = null,
    @SerialName("nb_pages") val nbPages: Int = 0,
    val genres: List<String> = emptyList(),
    val seo: String? = null,
    @SerialName("seo_serie") val seoSerie: String? = null,
    @SerialName("type_public") val typePublic: String? = null,
    val collection: String? = null,
    @SerialName("editeur_vo") val editeurVo: String? = null,
    @SerialName("date_parution_ebook") val dateParutionEbook: String? = null,
    val abonnement: Int = 0,
    @SerialName("nb_produits_serie") val nbProduitsSerie: Int = 1,
)

@Serializable
class FiltersResponse(
    val success: Boolean = false,
    val genres: List<FilterOption> = emptyList(),
    val ages: List<AgeOption> = emptyList(),
    val collections: List<FilterOption> = emptyList(),
    val types: List<FilterOption> = emptyList(),
)

@Serializable
class FilterOption(
    val id: Int,
    val nom: String,
)

@Serializable
class AgeOption(
    val id: Int,
    val age: String,
)

@Serializable
class LatestResponse(
    val success: Boolean = false,
    val ebooks: LatestEbooks? = null,
)

@Serializable
class LatestEbooks(
    val ebooks: List<LatestEbook> = emptyList(),
)

@Serializable
class LatestEbook(
    val ref: String,
    val titre: String,
    val auteur: String? = null,
    val seo: String,
    val genres: List<String> = emptyList(),
)

@Serializable
class LoginResponse(
    val success: Boolean = false,
    val token: String? = null,
    val message: String? = null,
)

@Serializable
class ImagesResponse(
    val success: Boolean = false,
    val images: List<ImageEntry> = emptyList(),
    val message: String? = null,
)

@Serializable
class ImageEntry(
    val p: Int = 0,
    val param: String = "",
)

@Serializable
class LoginRequest(
    val ident: String,
    val pass: String,
)
