package eu.kanade.tachiyomi.multisrc.bilibili

import java.text.DateFormatSymbols
import java.text.NumberFormat
import java.util.Locale

class BilibiliIntl(private val lang: String) {

    private val locale by lazy { Locale.forLanguageTag(lang) }

    private val dateFormatSymbols by lazy { DateFormatSymbols(locale) }

    private val numberFormat by lazy { NumberFormat.getInstance(locale) }

    val statusLabel: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "进度"
        SPANISH -> "Estado"
        else -> "Status"
    }

    val sortLabel: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "排序"
        INDONESIAN -> "Urutkan dengan"
        SPANISH -> "Ordenar por"
        else -> "Sort by"
    }

    val genreLabel: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "题材"
        SPANISH -> "Género"
        else -> "Genre"
    }

    val areaLabel: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "地区"
        else -> "Area"
    }

    val priceLabel: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "收费"
        INDONESIAN -> "Harga"
        SPANISH -> "Precio"
        else -> "Price"
    }

    fun hasPaidChaptersWarning(chapterCount: Int): String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE ->
            "${Bilibili.EMOJI_WARNING} 此漫画有 ${chapterCount.localized} 个付费章节，已在目录中隐藏。" +
                "如果你已购买，请在 WebView 登录并刷新目录，即可阅读已购章节。"
        SPANISH ->
            "${Bilibili.EMOJI_WARNING} ADVERTENCIA: Esta serie tiene ${chapterCount.localized} " +
                "capítulos pagos que fueron filtrados de la lista de capítulos. Si ya has " +
                "desbloqueado y tiene alguno en su cuenta, inicie sesión en WebView y " +
                "actualice la lista de capítulos para leerlos."
        else ->
            "${Bilibili.EMOJI_WARNING} WARNING: This series has ${chapterCount.localized} paid " +
                "chapters. If you have any unlocked in your account then sign in through WebView " +
                "to be able to read them."
    }

    val imageQualityPrefTitle: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "章节图片质量"
        INDONESIAN -> "Kualitas gambar"
        SPANISH -> "Calidad de imagen del capítulo"
        else -> "Chapter image quality"
    }

    val imageQualityPrefEntries: Array<String> = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> arrayOf("原图", "高清 (1600w)", "标清 (1000w)", "低清 (800w)")
        else -> arrayOf("Raw", "HD (1600w)", "SD (1000w)", "Low (800w)")
    }

    val imageFormatPrefTitle: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "章节图片格式"
        INDONESIAN -> "Format gambar"
        SPANISH -> "Formato de la imagen del capítulo"
        else -> "Chapter image format"
    }

    val sortInterest: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "为你推荐"
        INDONESIAN -> "Kamu Mungkin Suka"
        SPANISH -> "Sugerencia"
        else -> "Interest"
    }

    @Suppress("UNUSED") // In BilibiliManga
    val sortPopular: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "人气推荐"
        INDONESIAN -> "Populer"
        SPANISH -> "Popularidad"
        FRENCH -> "Préférences"
        else -> "Popular"
    }

    val sortUpdated: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "更新时间"
        INDONESIAN -> "Terbaru"
        SPANISH -> "Actualización"
        FRENCH -> "Récent"
        else -> "Updated"
    }

    @Suppress("UNUSED") // In BilibiliManga
    val sortAdded: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "上架时间"
        else -> "Added"
    }

    @Suppress("UNUSED") // In BilibiliManga
    val sortFollowers: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "追漫人数"
        else -> "Followers count"
    }

    val statusAll: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "全部"
        INDONESIAN -> "Semua"
        SPANISH -> "Todos"
        FRENCH -> "Tout"
        else -> "All"
    }

    val statusOngoing: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "连载中"
        INDONESIAN -> "Berlangsung"
        SPANISH -> "En curso"
        FRENCH -> "En cours"
        else -> "Ongoing"
    }

    val statusComplete: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "已完结"
        INDONESIAN -> "Tamat"
        SPANISH -> "Finalizado"
        FRENCH -> "Complet"
        else -> "Completed"
    }

    @Suppress("UNUSED") // In BilibiliManga
    val priceAll: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "全部"
        INDONESIAN -> "Semua"
        SPANISH -> "Todos"
        else -> "All"
    }

    @Suppress("UNUSED") // In BilibiliManga
    val priceFree: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "免费"
        INDONESIAN -> "Bebas"
        SPANISH -> "Gratis"
        else -> "Free"
    }

    @Suppress("UNUSED") // In BilibiliManga
    val pricePaid: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "付费"
        INDONESIAN -> "Dibayar"
        SPANISH -> "Pago"
        else -> "Paid"
    }

    @Suppress("UNUSED") // In BilibiliManga
    val priceWaitForFree: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "等就免费"
        else -> "Wait for free"
    }

    @Suppress("UNUSED") // In BilibiliComics
    val failedToRefreshToken: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "无法刷新令牌。请打开 WebView 修正错误。"
        SPANISH -> "Error al actualizar el token. Abra el WebView para solucionar este error."
        else -> "Failed to refresh the token. Open the WebView to fix this error."
    }

    @Suppress("UNUSED") // In BilibiliComics
    val failedToGetCredential: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "无法获取阅读章节所需的凭证。"
        SPANISH -> "Erro al obtener la credencial para leer el capítulo."
        else -> "Failed to get the credential to read the chapter."
    }

    val informationTitle: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "信息"
        SPANISH -> "Información"
        else -> "Information"
    }

    private val updatesDaily: String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "每日更新"
        SPANISH -> "Actualizaciones diarias"
        else -> "Updates daily"
    }

    private fun updatesEvery(days: String): String = when (lang) {
        CHINESE, SIMPLIFIED_CHINESE -> "${days}更新"
        SPANISH -> "Actualizaciones todos los $days"
        else -> "Updates every $days"
    }

    fun getUpdateDays(dayIndexes: List<Int>): String {
        val shortWeekDays = dateFormatSymbols.shortWeekdays.filterNot(String::isBlank)
        if (dayIndexes.size == shortWeekDays.size) return updatesDaily
        val shortWeekDaysUpperCased = shortWeekDays.map {
            it.replaceFirstChar { char -> char.uppercase(locale) }
        }

        val days = dayIndexes.joinToString { shortWeekDaysUpperCased[it] }
        return updatesEvery(days)
    }

    private val Int.localized: String
        get() = numberFormat.format(this)

    companion object {
        const val CHINESE = "zh"
        const val INDONESIAN = "id"
        const val SIMPLIFIED_CHINESE = "zh-Hans"
        const val SPANISH = "es"
        const val FRENCH = "fr"

        @Suppress("UNUSED") // In BilibiliComics
        const val ENGLISH = "en"
    }
}
