package eu.kanade.tachiyomi.multisrc.mccms

object Intl {
    var lang = "zh"

    val sort
        get() = when (lang) {
            "zh" -> "排序"
            else -> "Sort by"
        }

    val popular
        get() = when (lang) {
            "zh" -> "热门人气"
            else -> "Popular"
        }

    val latest
        get() = when (lang) {
            "zh" -> "更新时间"
            else -> "Latest"
        }

    val score
        get() = when (lang) {
            "zh" -> "评分"
            else -> "Score"
        }

    val status
        get() = when (lang) {
            "zh" -> "进度"
            else -> "Status"
        }

    val all
        get() = when (lang) {
            "zh" -> "全部"
            else -> "All"
        }

    val ongoing
        get() = when (lang) {
            "zh" -> "连载"
            else -> "Ongoing"
        }

    val completed
        get() = when (lang) {
            "zh" -> "完结"
            else -> "Completed"
        }

    val genreWeb
        get() = when (lang) {
            "zh" -> "标签"
            else -> "Genre"
        }

    val genreApi
        get() = when (lang) {
            "zh" -> "标签(搜索文本时无效)"
            else -> "Genre (ignored for text search)"
        }

    val categoryWeb
        get() = when (lang) {
            "zh" -> "分类筛选（搜索时无效）"
            else -> "Category filters (ignored for text search)"
        }

    val tapReset
        get() = when (lang) {
            "zh" -> "点击“重置”尝试刷新标签分类"
            else -> "Tap 'Reset' to load genres"
        }
}
