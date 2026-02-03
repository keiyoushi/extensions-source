package eu.kanade.tachiyomi.extension.all.xgmn

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

fun buildFilterList(): FilterList = FilterList(
    CategoryFilter(),
)

class CategoryFilter :
    Filter.Select<String>(
        "分类（搜索时无效）",
        arrayOf(
            "秀人网", "美媛馆", "尤物馆", "爱蜜社", "蜜桃社", "优星馆", "嗲囡囡",
            "魅妍社", "兔几盟", "影私荟", "星乐园", "顽味生活", "模范学院",
            "花の颜", "御女郎", "网红馆", "尤蜜荟", "薄荷叶", "瑞丝馆", "模特联盟",
            "花漾", "星颜社", "画语界", "推女郎", "尤果网", "青豆客", "头条女神",
            "果团网", "喵糖映画", "爱尤物", "波萝社", "猎女神", "尤蜜", "潘多拉",
            "Artgravia", "DJAWA", "丝袜美腿", "美腿宝贝", "蜜丝", "妖精社",
            "性感尤物", "国产美女", "港台美女", "日韩美女", "欧美美女", "丝袜美腿",
            "内衣尤物", "Cosplay",
        ),
    ) {
    override fun toString() = arrayOf(
        "Xiuren/", "MyGirl/", "YouWu/", "IMiss/", "MiiTao/", "Uxing/", "FeiLin/",
        "MiStar/", "Tukmo/", "WingS/", "LeYuan/", "Taste/", "MFStar/", "Huayan/",
        "DKGirl/", "Candy/", "YouMi/", "MintYe/", "Micat/", "Mtmeng/", "HuaYang/",
        "XingYan/", "XiaoYu/", "Tuigirl/", "Ugirls/", "Tgod/", "TouTiao/", "Girlt/",
        "Mtcos/", "Aiyouwu/", "BoLoli/", "Slady/", "YouMei/", "Pdl/", "Artgravia/",
        "DJAWA/", "Siwameitui/", "LEGBABY/", "MissLeg/", "YaoJingShe/", "Xgyw/",
        "Guochanmeinv/", "Gangtaimeinv/", "Rihanmeinv/", "Oumeimeinv/",
        "Siwameitui/", "Neiyiyouwu/", "Cosplay/",
    )[state]
}

// class XiurenFilter : Filter.Select<String>(
//     "秀人系列",
//     arrayOf(
//         "秀人网", "美媛馆", "尤物馆", "爱蜜社", "蜜桃社", "优星馆", "嗲囡囡",
//         "魅妍社", "兔几盟", "影私荟", "星乐园", "顽味生活", "模范学院",
//         "花の颜", "御女郎", "网红馆", "尤蜜荟", "薄荷叶", "瑞丝馆", "模特联盟",
//         "花漾", "星颜社", "画语界",
//     ),
// ) {
//     val value
//         get() = arrayOf(
//             "Xiuren", "MyGirl", "YouWu", "IMiss", "MiiTao", "Uxing", "FeiLin",
//             "MiStar", "Tukmo", "WingS", "LeYuan", "Taste", "MFStar", "Huayan",
//             "DKGirl", "Candy", "YouMi", "MintYe", "Micat", "Mtmeng", "HuaYang",
//             "XingYan", "XiaoYu",
//         )[state]
// }
//
// class MingzhanFilter : Filter.Select<String>(
//     "名站系列",
//     arrayOf(
//         "推女郎", "尤果网", "青豆客", "头条女神", "果团网", "喵糖映画",
//         "爱尤物", "波萝社", "猎女神", "尤蜜", "潘多拉", "Artgravia", "DJAWA",
//     ),
// ) {
//     val value
//         get() = arrayOf(
//             "Tuigirl", "Ugirls", "Tgod", "TouTiao", "Girlt", "Mtcos", "Aiyouwu",
//             "BoLoli", "Slady", "YouMei", "Pdl", "Artgravia", "DJAWA",
//         )[state]
// }
//
// class SiwameituiFilter :
//     Filter.Select<String>("丝袜美腿", arrayOf("丝袜美腿", "美腿宝贝", "蜜丝", "妖精社")) {
//     val value get() = arrayOf("siwametui", "LEGBABY", "MissLeg", "YaoJingShe")[state]
// }
//
// class JingpinFilter : Filter.Select<String>(
//     "精品散图",
//     arrayOf(
//         "性感尤物", "国产美女", "港台美女", "日韩美女",
//         "欧美美女", "丝袜美腿", "内衣尤物", "Cosplay",
//     ),
// ) {
//     val value
//         get() = arrayOf(
//             "Xgyw", "Guochanmeinv", "Gangtaimeinv", "Rihanmeinv",
//             "Oumeimeinv", "Siwameitui", "Neiyiyouwu", "Cosplay",
//         )[state]
// }
