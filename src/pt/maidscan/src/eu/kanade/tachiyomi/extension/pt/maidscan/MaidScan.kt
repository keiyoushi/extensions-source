package eu.kanade.tachiyomi.extension.pt.maidscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MaidScan : HttpSource() {

    override val name = "Maid Scan"
    override val baseUrl = "https://empreguetes.xyz"
    override val lang = "pt-BR"
    override val supportsLatest = true

    // API de dados
    private val apiUrl = "https://api.verdinha.wtf"

    // Servidor de Imagens (CDN)
    private val cdnUrl = "https://cdn.verdinha.wtf"

    // ID Padrão do site (Empreguetes = 3)
    private val defaultScanId = "3"

    private val json: Json by injectLazy()

    // Formato de data para ordenação correta
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Headers otimizados
    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .set("scan-id", defaultScanId)

    // --- POPULARES E ATUALIZAÇÕES ---

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/obras/ranking?tipo=visualizacoes_geral&pagina=$page&limite=24&gen_id=4", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<JsonObject>(json)
        val obras = result["obras"]?.jsonArray ?: return MangasPage(emptyList(), false)

        val mangas = obras.map { element ->
            val obj = element.jsonObject
            SManga.create().apply {
                // Título obrigatório
                title = obj["obr_nome"]?.jsonPrimitive?.contentOrNull!!

                // URL/Slug obrigatório
                val slug = obj["obr_slug"]?.jsonPrimitive?.contentOrNull!!
                url = "/obra/$slug"

                // Montagem da URL da capa DINÂMICA
                val obrId = obj["obr_id"]?.jsonPrimitive?.contentOrNull
                val imgFile = obj["obr_imagem"]?.jsonPrimitive?.contentOrNull
                val itemScanId = obj["scan_id"]?.jsonPrimitive?.contentOrNull ?: defaultScanId

                thumbnail_url = if (imgFile != null && obrId != null) {
                    "$cdnUrl/scans/$itemScanId/obras/$obrId/$imgFile"
                } else {
                    null
                }
            }
        }

        val totalPaginas = result["totalPaginas"]?.jsonPrimitive?.int ?: 1
        val paginaAtual = result["pagina"]?.jsonPrimitive?.int ?: 1
        val hasNextPage = paginaAtual < totalPaginas

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/obras/atualizacoes?pagina=$page&limite=24", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // --- PESQUISA ---

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras/search".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("obr_nome", query)
            .addQueryParameter("todos_generos", "1")

        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    url.addQueryParameter("orderBy", filter.toUriPart())
                    url.addQueryParameter("orderDirection", if (filter.state!!.ascending) "ASC" else "DESC")
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        SortFilter(),
    )

    // --- DETALHES DO MANGÁ ---

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/obras/$slug", headers)
    }

    // CORREÇÃO CRÍTICA: Garante que o WebView abra o site e não a API
    override fun getMangaUrl(manga: SManga): String {
        return baseUrl + manga.url
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val obj = response.parseAs<JsonObject>(json)
        val obra = if (obj.containsKey("obra")) obj["obra"]!!.jsonObject else obj

        return SManga.create().apply {
            // Título obrigatório
            title = obra["obr_nome"]?.jsonPrimitive?.contentOrNull!!
            description = obra["obr_descricao"]?.jsonPrimitive?.contentOrNull ?: obra["obr_sinopse"]?.jsonPrimitive?.contentOrNull

            val statusName = obra["status"]?.jsonObject?.get("stt_nome")?.jsonPrimitive?.contentOrNull
                ?: obra["obr_status"]?.jsonPrimitive?.contentOrNull

            status = when (statusName?.lowercase()) {
                "ativo", "em andamento" -> SManga.ONGOING
                "completo", "concluído" -> SManga.COMPLETED
                "hiato" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            val genres = mutableListOf<String>()
            obra["genero"]?.jsonObject?.get("gen_nome")?.jsonPrimitive?.contentOrNull?.let { genres.add(it) }
            obra["tags"]?.jsonArray?.forEach {
                it.jsonObject["tag_nome"]?.jsonPrimitive?.contentOrNull?.let { tag -> genres.add(tag) }
            }
            genre = genres.distinct().joinToString(", ")

            // Capa com Scan ID Dinâmico
            val obrId = obra["obr_id"]?.jsonPrimitive?.contentOrNull
            val imgFile = obra["obr_imagem"]?.jsonPrimitive?.contentOrNull
            val itemScanId = obra["scan_id"]?.jsonPrimitive?.contentOrNull ?: defaultScanId

            thumbnail_url = if (imgFile != null && obrId != null) {
                "$cdnUrl/scans/$itemScanId/obras/$obrId/$imgFile"
            } else {
                null
            }
        }
    }

    // --- CAPÍTULOS ---

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val obj = response.parseAs<JsonObject>(json)
        val obra = if (obj.containsKey("obra")) obj["obra"]!!.jsonObject else obj
        val capitulos = obra["capitulos"]?.jsonArray ?: return emptyList()

        return capitulos.map { element ->
            val cap = element.jsonObject
            SChapter.create().apply {
                name = cap["cap_nome"]?.jsonPrimitive?.contentOrNull ?: "Capítulo ${cap["cap_numero"]}"
                chapter_number = cap["cap_numero"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: -1f

                val id = cap["cap_id"]?.jsonPrimitive?.contentOrNull
                url = "/capitulo/$id"

                val dateStr = cap["cap_criado_em"]?.jsonPrimitive?.contentOrNull
                    ?: cap["cap_liberar_em"]?.jsonPrimitive?.contentOrNull

                date_upload = dateFormat.tryParse(dateStr)
            }
        }.sortedWith(
            compareByDescending<SChapter> { it.chapter_number }
                .thenByDescending { it.date_upload },
        )
    }

    // --- PÁGINAS (LEITOR) ---

    override fun pageListRequest(chapter: SChapter): Request {
        val capId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/capitulos/$capId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val obj = response.parseAs<JsonObject>(json)

        // Extração Dinâmica de IDs para Fallback
        val obrId = obj["obr_id"]?.jsonPrimitive?.contentOrNull
            ?: obj["obra"]?.jsonObject?.get("obr_id")?.jsonPrimitive?.contentOrNull

        // Tenta pegar o scan_id do objeto obra aninhado ou da raiz
        val itemScanId = obj["obra"]?.jsonObject?.get("scan_id")?.jsonPrimitive?.contentOrNull
            ?: obj["scan_id"]?.jsonPrimitive?.contentOrNull
            ?: defaultScanId

        val capNum = obj["cap_numero"]?.jsonPrimitive?.contentOrNull

        val paginas = obj["cap_paginas"]?.jsonArray
            ?: obj["paginas"]?.jsonArray
            ?: obj["imagens"]?.jsonArray
            ?: return emptyList()

        return paginas.mapIndexed { index, element ->
            var imgUrl: String? = null

            if (element is JsonObject) {
                val rawPath = element.jsonObject["path"]?.jsonPrimitive?.contentOrNull
                val cleanPath = rawPath?.trim { it == '/' }

                val imgName = element.jsonObject["src"]?.jsonPrimitive?.contentOrNull
                    ?: element.jsonObject["img_nome"]?.jsonPrimitive?.contentOrNull

                if (cleanPath != null) {
                    if (imgName != null) {
                        if (cleanPath.endsWith(imgName, ignoreCase = true)) {
                            imgUrl = "$cdnUrl/$cleanPath"
                        } else {
                            imgUrl = "$cdnUrl/$cleanPath/$imgName"
                        }
                    } else {
                        imgUrl = "$cdnUrl/$cleanPath"
                    }
                }
            }

            // Fallback usando o Scan ID dinâmico
            if (imgUrl == null) {
                val imgName = if (element is JsonObject) {
                    element.jsonObject["src"]?.jsonPrimitive?.contentOrNull
                        ?: element.jsonObject["img_nome"]?.jsonPrimitive?.contentOrNull
                } else {
                    element.jsonPrimitive.contentOrNull
                }

                if (imgName != null && obrId != null && capNum != null) {
                    imgUrl = "$cdnUrl/scans/$itemScanId/obras/$obrId/capitulos/$capNum/$imgName"
                }
            }

            Page(index, "", imgUrl ?: "")
        }
    }

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/")
            .set("scan-id", defaultScanId)
            .removeAll("Origin")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun imageUrlParse(response: Response): String = ""

    private class SortFilter : Filter.Sort(
        "Ordenar por",
        arrayOf("Última Atualização", "Visualizações", "Nome", "Data de Criação"),
        Selection(0, false),
    ) {
        val vals = arrayOf("ultima_atualizacao", "visualizacoes", "obr_nome", "criado_em")
        fun toUriPart() = vals[state!!.index]
    }

    private inline fun <reified T> Response.parseAs(json: Json): T =
        json.decodeFromString(body.string())

    private fun SimpleDateFormat.tryParse(string: String?): Long {
        if (string == null) return 0L
        return try {
            parse(string)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
