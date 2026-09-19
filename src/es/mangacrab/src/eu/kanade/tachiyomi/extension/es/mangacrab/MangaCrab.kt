package eu.kanade.tachiyomi.extension.es.mangacrab

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.addRandomUAPreference
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class MangaCrab :
    KeiSource(),
    ConfigurableSource {

    private val dateFormat = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss",
        Locale.US,
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addRandomUAPreference()
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    private suspend fun getMangasPage(
        page: Int,
        query: String = "",
        genres: String = "",
        feed: String = "discover",
        ranking: String = "",
        status: String = "",
        origin: String = "",
    ): MangasPage {
        val url = "$baseUrl/api/mv/mangas"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", PER_PAGE.toString())
            .addQueryParameter("search", query)
            .addQueryParameter("genres", genres)
            .addQueryParameter("status", status)
            .addQueryParameter("origin", origin)
            .addQueryParameter("ranking", ranking)
            .addQueryParameter("feed", feed)
            .addQueryParameter("nsfw", "false")
            .addQueryParameter("nsfw_only", "false")
            .build()

        val dto = client
            .get(url)
            .parseAs<MangaCrabMangasDto>()

        return MangasPage(
            mangas = dto.items.map { it.asSManga() },
            hasNextPage = dto.pagination.has_next,
        )
    }

    override suspend fun getPopularManga(page: Int): MangasPage = getMangasPage(
        page = page,
        feed = "discover",
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangasPage(
        page = page,
        feed = "updated",
    )

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val genres = filters
            .filterIsInstance<GenreFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        val feed = filters
            .filterIsInstance<FeedFilter>()
            .firstOrNull()
            ?.selectedValue
            ?: "discover"

        val ranking = filters
            .filterIsInstance<RankingFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        val status = filters
            .filterIsInstance<StatusFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        val origin = filters
            .filterIsInstance<OriginFilter>()
            .firstOrNull()
            ?.selectedValue
            .orEmpty()

        return getMangasPage(
            page = page,
            query = query,
            genres = genres,
            feed = feed,
            ranking = ranking,
            status = status,
            origin = origin,
        )
    }

    override fun getFilterList(
        data: kotlinx.serialization.json.JsonElement?,
    ) = FilterList(
        Filter.Header("Usa los filtros para limitar el catálogo"),
        Filter.Separator(),
        GenreFilter(),
        FeedFilter(),
        RankingFilter(),
        StatusFilter(),
        OriginFilter(),
    )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url
            .removePrefix("/series/")
            .removeSuffix("/")
            .substringBefore("/")

        val detailsDto = client
            .get("$baseUrl/api/mv/mangas/by-slug/$slug")
            .parseAs<MangaCrabMangaDto>()

        val chaptersDto = client
            .get(
                "$baseUrl/api/mv/mangas/${detailsDto.id}/chapters?per_page=$CHAPTERS_PER_PAGE",
            )
            .parseAs<MangaCrabChaptersDto>()

        return SMangaUpdate(
            manga = detailsDto.asSManga(),
            chapters = chaptersDto.items.map { chapter ->
                chapter.asSChapter(detailsDto.id)
            },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val fragment = chapter.url.substringAfter("#", "")

        val mangaId = fragment
            .substringAfter("mangaId=", "")
            .substringBefore("&")

        val chapterIndex = fragment
            .substringAfter("capIndex=", "")
            .substringBefore("&")

        if (mangaId.isBlank() || chapterIndex.isBlank()) {
            throw Exception("No se pudo obtener la información del capítulo")
        }

        val chapterDto = client
            .get(
                "$baseUrl/api/mv/mangas/$mangaId/chapter?cap_index=$chapterIndex",
            )
            .parseAs<MangaCrabChapterDto>()

        if (chapterDto.is_locked || chapterDto.is_vip_chapter || !chapterDto.can_download) {
            throw Exception("Este capítulo está bloqueado o requiere VIP")
        }

        val securityHeader = chapterDto.security
            ?.takeIf { it.enabled == 1 }
            ?.header
            .orEmpty()

        return chapterDto.content
            ?.pages
            ?.mapIndexed { index, imageUrl ->
                val imageUrlWithHeader = if (securityHeader.isBlank()) {
                    imageUrl
                } else {
                    "$imageUrl#nodeHeader=$securityHeader"
                }

                Page(
                    index = index,
                    imageUrl = imageUrlWithHeader,
                )
            }
            .orEmpty()
    }

    override fun imageRequest(page: Page): Request {
        val pageUrl = page.imageUrl.orEmpty()
        val imageUrl = pageUrl.substringBefore("#nodeHeader=")
        val securityHeader = pageUrl.substringAfter("#nodeHeader=", "")

        return GET(
            imageUrl,
            headersBuilder()
                .add("Referer", "$baseUrl/")
                .add("Origin", baseUrl)
                .add("Accept", "*/*")
                .apply {
                    if (securityHeader.isNotBlank()) {
                        add("fansy", securityHeader)
                    }
                }
                .build(),
        )
    }

    private fun MangaCrabMangaDto.asSManga(): SManga = SManga.create().apply {
        title = this@asSManga.title
        setUrlWithoutDomain(this@asSManga.permalink)
        thumbnail_url = this@asSManga.cover

        description = this@asSManga.description
            .replace("\r\n", "\n")
            .replace("&quot;", "\"")

        genre = this@asSManga.genres
            .joinToString(", ") { it.name }

        status = when (this@asSManga.status?.raw) {
            "on-going", "en curso" -> SManga.ONGOING
            "end", "finalizado" -> SManga.COMPLETED
            "canceled" -> SManga.CANCELLED
            "on-hold", "hiato" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun MangaCrabChapterDto.asSChapter(mangaId: Long): SChapter {
        val chapterUrl = "$link#mangaId=$mangaId&capIndex=$index"

        return SChapter.create().apply {
            name = buildString {
                if (is_locked || is_vip_chapter || !can_download) {
                    append("🔒 ")
                }
                append(title.ifBlank { label })
            }
            setUrlWithoutDomain(chapterUrl)
            date_upload = dateFormat.parse(date)?.time ?: 0L
        }
    }

    private class FeedFilter :
        Filter.Select<String>(
            name = "Orden",
            values = arrayOf(
                "Descubre",
                "Nuevos",
                "Actualizados",
            ),
        ) {
        val selectedValue: String
            get() = when (state) {
                1 -> "new"
                2 -> "updated"
                else -> "discover"
            }
    }

    private class RankingFilter :
        Filter.Select<String>(
            name = "Ranking",
            values = arrayOf(
                "Sin ranking",
                "Vistas diarias",
                "Vistas semanales",
                "Vistas mensuales",
            ),
        ) {
        val selectedValue: String
            get() = when (state) {
                1 -> "daily"
                2 -> "weekly"
                3 -> "monthly"
                else -> ""
            }
    }

    private class StatusFilter :
        Filter.Select<String>(
            name = "Estado",
            values = arrayOf(
                "Todos",
                "En curso",
                "Finalizado",
                "Cancelado",
                "Hiato",
                "Próximo",
            ),
        ) {
        val selectedValue: String
            get() = when (state) {
                1 -> "on-going"
                2 -> "end"
                3 -> "canceled"
                4 -> "on-hold"
                5 -> "upcoming"
                else -> ""
            }
    }

    private class OriginFilter :
        Filter.Select<String>(
            name = "Origen",
            values = arrayOf(
                "Todos",
                "Manga",
                "Manhua",
                "Manhwa",
            ),
        ) {
        val selectedValue: String
            get() = when (state) {
                1 -> "manga"
                2 -> "manhua"
                3 -> "manhwa"
                else -> ""
            }
    }

    private class GenreFilter :
        Filter.Select<String>(
            name = "Género",
            values = GENRES.map { it.first }.toTypedArray(),
        ) {

        val selectedValue: String
            get() = GENRES[state].second

        private companion object {
            val GENRES = arrayOf(
                "Todos" to "",
                "+15" to "15",
                "Academia" to "academia",
                "acccion" to "acccion",
                "Acción" to "accion",
                "Action" to "action",
                "Adventure" to "adventure",
                "Aliado" to "aliado",
                "Amor" to "amor",
                "Ángeles" to "angeles",
                "Animación" to "animacion",
                "Anti-heroe" to "anti-heroe",
                "Apocalipsis" to "apocalipsis",
                "Apocalíptico" to "apocaliptico",
                "Apocalipto" to "apocalipto",
                "Artes marcial" to "artes-marcial",
                "Artes Marciales" to "artes-marciales",
                "Aventura" to "aventura",
                "Aventura Drama" to "aventura-drama",
                "Bestias invocadas" to "bestias-invocadas",
                "Caballeros" to "caballeros",
                "Cartoon" to "cartoon",
                "Cash" to "cash",
                "Cazador" to "cazador",
                "Ciencia Ficción" to "ciencia-ficcion",
                "Combate" to "combate",
                "Comedia" to "comedia",
                "Comedy" to "comedy",
                "Comida" to "comida",
                "Conspiracion" to "conspiracion",
                "Contrato" to "contrato",
                "Corrupción" to "corrupcion",
                "Creador de ciudades" to "creador-de-ciudades",
                "Crimen" to "crimen",
                "Cultivación" to "cultivacion",
                "Cultivo" to "cultivo",
                "Delincuentes" to "delincuentes",
                "Demonio" to "demonio",
                "Demonios" to "demonios",
                "Deporte" to "deporte",
                "Detective" to "detective",
                "Dinastía Joseon" to "dinastia-joseon",
                "Dioses" to "dioses",
                "Domador de bestias" to "domador-de-bestias",
                "Drama" to "drama",
                "Ecchi" to "ecchi",
                "Erotico" to "erotico",
                "Escolar" to "escolar",
                "Espíritus" to "espiritus",
                "estrategia" to "estrategia",
                "Evolución" to "evolucion",
                "Exclusivo" to "exclusivo",
                "Familia" to "familia",
                "Familia Real" to "familia-real",
                "Fantansía" to "fantansia",
                "Fantasia" to "fantasia",
                "Fantasía moderna" to "fantasia-moderna",
                "Fantasy" to "fantasy",
                "Favoritos" to "favoritos",
                "Game" to "game",
                "Género Bender" to "genero-bender",
                "Gore" to "gore",
                "Guerra" to "guerra",
                "Habilidades" to "habilidades",
                "Harem" to "harem",
                "Harem Inverso" to "harem-inverso",
                "Haren" to "haren",
                "Hentai" to "hentai",
                "Hermes" to "hermes",
                "Heroe" to "heroe",
                "Heroe Villano" to "heroe-villano",
                "Historia" to "historia",
                "Historical" to "historical",
                "Historico" to "historico",
                "Horror" to "horror",
                "Inmersión" to "inmersion",
                "Intriga" to "intriga",
                "Inversiones" to "inversiones",
                "Invocación" to "invocacion",
                "Isekai" to "isekai",
                "Juego" to "juego",
                "Juego en Linea" to "juego-en-linea",
                "Ladies" to "ladies",
                "Madrastra" to "madrastra",
                "Magia" to "magia",
                "Magnate" to "magnate",
                "Maldad" to "maldad",
                "Manga" to "manga",
                "Manhua" to "manhua",
                "Manhwa" to "manhwa",
                "Martial Arts" to "martial-arts",
                "Mature" to "mature",
                "Mazmorras" to "mazmorras",
                "MC" to "mc",
                "mc chambeador" to "mc-chambeador",
                "MC inteligente" to "mc-inteligente",
                "mc medico" to "mc-medico",
                "MC OP" to "mc-op",
                "Mecha" to "mecha",
                "Medicina" to "medicina",
                "Medieval" to "medieval",
                "Meian" to "meian",
                "Milf" to "milf",
                "Militar" to "militar",
                "Misterio" to "misterio",
                "Monstruo" to "monstruo",
                "Monstruos" to "monstruos",
                "Muchas Waifus" to "muchas-waifus",
                "Mujer casada" to "mujer-casada",
                "Mujer mayor" to "mujer-mayor",
                "Murim" to "murim",
                "Música" to "musica",
                "Mystery" to "mystery",
                "Nigromante" to "nigromante",
                "No Princeso" to "no-princeso",
                "Novela" to "novela",
                "Novela Ligera" to "novela-ligera",
                "NTR" to "ntr",
                "Nuevo" to "nuevo",
                "OP" to "op",
                "Original" to "original",
                "Otra oportunidad" to "otra-oportunidad",
                "Paladines" to "paladines",
                "Pareja casada" to "pareja-casada",
                "Parodia" to "parodia",
                "Peleas" to "peleas",
                "Poderes sobrenaturales" to "poderes-sobrenaturales",
                "Posible Harem" to "posible-harem",
                "Post-apocalíptico" to "post-apocaliptico",
                "Postapocalíptico" to "postapocaliptico",
                "Prota" to "prota",
                "Prota Badas" to "prota-badas",
                "Prota OP" to "prota-op",
                "Próximamente" to "proximamente",
                "Psicologico" to "psicologico",
                "Puto-Amo" to "puto-amo",
                "Realidad" to "realidad",
                "Realidad Virtual" to "realidad-virtual",
                "Recomendado" to "recomendado",
                "Recuentos de la vida" to "recuentos-de-la-vida",
                "Recuerdo de la vida" to "recuerdo-de-la-vida",
                "Reencarnación" to "reencarnacion",
                "reencarnacion" to "reencarnacion-2",
                "Reencarnado" to "reencarnado",
                "Regresión" to "regresion",
                "Reincarnation" to "reincarnation",
                "Relacion secreta" to "relacion-secreta",
                "Renacimiento" to "renacimiento",
                "Retornado" to "retornado",
                "Retorno" to "retorno",
                "Rey Demonio" to "rey-demonio",
                "Romance" to "romance",
                "Romance? Quien sabe" to "romance-quien-sabe",
                "RPG" to "rpg",
                "Samurai" to "samurai",
                "Sci-fi" to "sci-fi",
                "Seinen" to "seinen",
                "Shonen" to "shonen",
                "Shoujo" to "shoujo",
                "Shounen" to "shounen",
                "Sistema" to "sistema",
                "Sistema de Niveles" to "sistema-de-niveles",
                "Sistemas" to "sistemas",
                "sistemas de trucos" to "sistemas-de-trucos",
                "Slice of Life" to "slice-of-life",
                "Sobrenatural" to "sobrenatural",
                "Super poderes" to "super-poderes",
                "Supernatural" to "supernatural",
                "Superpoderes" to "superpoderes",
                "Supervivencia" to "supervivencia",
                "Suspenso" to "suspenso",
                "Telenovela" to "telenovela",
                "Thriller" to "thriller",
                "Tragedia" to "tragedia",
                "Tragico" to "tragico",
                "Transmigración" to "transmigracion",
                "Transmigración entre mundos" to "transmigracion-entre-mundos",
                "Urbano" to "urbano",
                "Vampiros" to "vampiros",
                "Venganza" to "venganza",
                "viaje en el tiempo" to "viaje-en-el-tiempo",
                "Vida Cotidiana" to "vida-cotidiana",
                "Vida Escolar" to "vida-escolar",
                "video juegos" to "video-juegos",
                "Villano" to "villano",
                "waifus" to "waifus",
                "Web Comic" to "web-comic",
                "Webtoon" to "webtoon",
                "Zombies" to "zombies",
            )
        }
    }

    companion object {
        private const val PER_PAGE = 24
        private const val CHAPTERS_PER_PAGE = 5000
    }
}
