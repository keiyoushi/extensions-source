package eu.kanade.tachiyomi.extension.zh.favcomic

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList() = FilterList(
    Filter.Header("筛选条件（漫画类型选择“全部”时，其余条件无效）"),
    MangaTypeFilter(),
    OriginFilter(),
    StatusFilter(),
    FreeFilter(),
    SortFilter(),
    Filter.Separator(),
    TagGroup(),
)

class MangaTypeFilter : Filter.Select<String>("类型", arrayOf("全部", "少男漫画", "少女漫画", "性感图库", "成人漫画")) {
    override fun toString() = arrayOf("search", "boy", "girl", "picture", "r18")[state]
}

class OriginFilter : Filter.Select<String>("地区", arrayOf("全部", "日本", "韩国", "大陆及港台", "其它")) {
    override fun toString() = arrayOf("0", "2", "3", "1", "4")[state]
}

class StatusFilter : Filter.Select<String>("状态", arrayOf("全部", "连载", "完结")) {
    override fun toString() = arrayOf("0", "1", "2")[state]
}

class FreeFilter : Filter.Select<String>("门槛", arrayOf("全部", "免费", "付费")) {
    override fun toString() = arrayOf("0", "1", "2")[state]
}

class SortFilter : Filter.Select<String>("排序", arrayOf("人气推荐", "更新时间")) {
    override fun toString() = arrayOf("1", "2")[state]
}

class TagGroup :
    Filter.Group<TagFilter>(
        "分组标签（只有上面选中的漫画“类型”会生效）",
        listOf(
            TagFilter(
                "少男漫画",
                arrayOf(
                    "全部", "奇幻", "冒险", "异世界", "爱情", "鬼神", "推理悬疑", "热血", "恐怖·惊悚",
                    "节操", "校园", "搞笑喜剧", "科幻", "后宫", "格斗", "运动竞技", "穿越", "动作",
                    "战争", "音乐", "轻小说", "励志", "武侠", "短篇", "修真", "百合", "剧情", "美食家",
                    "历史", "职场", "性转换", "伪娘", "黑道", "其他",
                ),
                arrayOf(
                    "0", "1", "4", "7", "36", "5", "2", "3", "11", "41", "34", "6", "42", "40",
                    "32", "9", "88", "80", "16", "39", "112", "15", "13", "35", "89", "118",
                    "79", "12", "10", "43", "45", "83", "8", "14",
                ),
            ),
            TagFilter(
                "少女漫画",
                arrayOf(
                    "全部", "恋爱", "TL", "BL", "欢乐向", "校园", "百合", "后宫·宮廷", "轻小说",
                    "剧情", "美食", "职场", "治愈", "舞蹈音乐", "性转换", "伪娘", "萌系", "重生",
                    "生活日常", "宠物", "ABO", "霸道总裁", "古风", "短篇", "其他", "NTR", "扶他",
                    "异世界", "被NTR",
                ),
                arrayOf(
                    "0", "17", "33", "19", "31", "18", "21", "28", "44", "81", "38", "26",
                    "24", "20", "25", "82", "22", "37", "27", "30", "125", "29", "23", "46",
                    "75", "129", "131", "122", "132",
                ),
            ),
            TagFilter(
                "性感图库",
                arrayOf("全部", "Cosplay", "AI生成", "CG画集", "写真", "OnlyFans"),
                arrayOf("0", "76", "87", "85", "86", "77"),
            ),
            TagFilter(
                "成人漫画",
                arrayOf(
                    "全部", "剧情向", "无修正", "同人", "巨乳", "制服·JK", "口交", "近亲·乱伦",
                    "熟女", "伪娘", "NTR", "贫乳", "束缚·捆绑", "眼镜", "3P·群交", "丝袜", "人外",
                    "辣妹", "情趣内衣", "浪漫爱情", "兽耳", "调教", "百合", "乳交", "触手",
                    "妖精·魅魔", "重口", "兔女郎", "黑肉", "强暴", "扶他", "人妻", "足交", "处女",
                    "痴女", "痴汉·丑男", "性玩具", "颜射", "泳装", "出汗", "多毛", "舔阴", "双马尾",
                    "自慰", "肛交", "中出", "修女", "furry", "排尿", "倒乳头", "AI生成", "男娘",
                    "受孕", "可爱", "护士", "催眠", "潮吹", "肉感女", "女仆", "双穴", "长筒袜",
                    "正太", "3D", "露出", "阿黑颜", "yaoi",
                ),
                arrayOf(
                    "0", "48", "47", "110", "49", "68", "52", "62", "50", "93", "55", "94",
                    "108", "56", "63", "104", "111", "65", "64", "78", "60", "58", "90",
                    "51", "91", "95", "106", "109", "59", "57", "101", "54", "70", "97",
                    "98", "107", "61", "99", "100", "73", "74", "102", "67", "69", "96",
                    "53", "133", "130", "72", "128", "123", "117", "124", "121", "105",
                    "126", "71", "120", "127", "115", "66", "119", "114", "103", "113", "116",
                ),
            ),
        ),
    ) {
    fun getTag(i: Int) = state.getOrNull(i - 1)?.toString()
}

class TagFilter(name: String, tags: Array<String>, val v: Array<String>) : Filter.Select<String>(name, tags) {
    override fun toString() = v[state]
}
