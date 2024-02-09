package eu.kanade.tachiyomi.multisrc.colamanga

class ColaMangaIntl(private val lang: String) {

    val rateLimitPrefTitle = when (lang) {
        "zh" -> "主站连接限制"
        else -> "Rate limit"
    }

    fun rateLimitPrefSummary(defaultValue: String) = when (lang) {
        "zh" -> "此值影响主站的连接请求量。降低此值可以减少获得HTTP 403错误的几率，但加载速度也会变慢。需要重启软件以生效。\n默认值：$defaultValue\n当前值：%s"
        else -> "Number of requests made to the website. Lowering this value may reduce the chance of getting HTTP 403. Tachiyomi restart required.\nDefault value: $defaultValue\nCurrent value: %s"
    }

    val rateLimitPeriodPrefTitle = when (lang) {
        "zh" -> "主站连接限制期"
        else -> "Rate limit period"
    }

    fun rateLimitPeriodPrefSummary(defaultValue: String) = when (lang) {
        "zh" -> "此值影响主站点连接限制时的延迟（毫秒）。增加这个值可能会减少出现HTTP 403错误的机会，但加载速度也会变慢。需要重启软件以生效。\n默认值：$defaultValue\n当前值：%s"
        else -> "Time in milliseconds to wait after using up all allowed requests. Lowering this value may reduce the chance of getting HTTP 403. Tachiyomi restart required.\nDefault value: $defaultValue\nCurrent value: %s"
    }

    val timedOutDecryptingImageLinks = when (lang) {
        else -> "Timed out decrypting image links"
    }

    val couldNotDeobufscateScript = when (lang) {
        else -> "Could not deobfuscate script"
    }

    fun couldNotFindKey(forKeyType: String) = when (lang) {
        else -> "Could not find key for keyType $forKeyType"
    }

    val searchType = when (lang) {
        "zh" -> "搜索类型"
        else -> "Search type"
    }

    val searchTypeFuzzy = when (lang) {
        "zh" -> "模糊"
        else -> "Fuzzy"
    }

    val searchTypeExact = when (lang) {
        "zh" -> "精确"
        else -> "Exact"
    }
}
