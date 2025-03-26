package eu.kanade.tachiyomi.extension.all.misskon

import eu.kanade.tachiyomi.extension.all.misskon.SourceCategorySelector.Companion.CategoryPresets.CHINESE
import eu.kanade.tachiyomi.extension.all.misskon.SourceCategorySelector.Companion.CategoryPresets.KOREAN
import eu.kanade.tachiyomi.extension.all.misskon.SourceCategorySelector.Companion.CategoryPresets.OTHER
import eu.kanade.tachiyomi.extension.all.misskon.SourceCategorySelector.Companion.CategoryPresets.TOP
import eu.kanade.tachiyomi.source.model.Filter

data class SourceCategory(private val name: String, val url: String) {
    override fun toString() = this.name
}

class SourceCategorySelector(
    name: String,
    categories: List<SourceCategory>,
) : Filter.Select<SourceCategory>(name, categories.toTypedArray()) {

    val selectedCategory: SourceCategory?
        get() = if (state > 0) values[state] else null

    companion object {

        private object CategoryPresets {

            val TOP = listOf(
                SourceCategory("Top 3 days", "/top3/"),
                SourceCategory("Top 7 days", "https://misskon.com/top7/"),
                SourceCategory("Top 30 days", "https://misskon.com/top30/"),
                SourceCategory("Top 60 days", "https://misskon.com/top60/"),
            )

            val CHINESE = listOf(
                SourceCategory("Chinese:[MTCos] 喵糖映画", "https://misskon.com/tag/mtcos/"),
                SourceCategory("Chinese:BoLoli", "https://misskon.com/tag/bololi/"),
                SourceCategory("Chinese:CANDY", "https://misskon.com/tag/candy/"),
                SourceCategory("Chinese:FEILIN", "https://misskon.com/tag/feilin/"),
                SourceCategory("Chinese:FToow", "https://misskon.com/tag/ftoow/"),
                SourceCategory("Chinese:GIRLT", "https://misskon.com/tag/girlt/"),
                SourceCategory("Chinese:HuaYan", "https://misskon.com/tag/huayan/"),
                SourceCategory("Chinese:HuaYang", "https://misskon.com/tag/huayang/"),
                SourceCategory("Chinese:IMISS", "https://misskon.com/tag/imiss/"),
                SourceCategory("Chinese:ISHOW", "https://misskon.com/tag/ishow/"),
                SourceCategory("Chinese:JVID", "https://misskon.com/tag/jvid/"),
                SourceCategory("Chinese:KelaGirls", "https://misskon.com/tag/kelagirls/"),
                SourceCategory("Chinese:Kimoe", "https://misskon.com/tag/kimoe/"),
                SourceCategory("Chinese:LegBaby", "https://misskon.com/tag/legbaby/"),
                SourceCategory("Chinese:MF", "https://misskon.com/tag/mf/"),
                SourceCategory("Chinese:MFStar", "https://misskon.com/tag/mfstar/"),
                SourceCategory("Chinese:MiiTao", "https://misskon.com/tag/miitao/"),
                SourceCategory("Chinese:MintYe", "https://misskon.com/tag/mintye/"),
                SourceCategory("Chinese:MISSLEG", "https://misskon.com/tag/missleg/"),
                SourceCategory("Chinese:MiStar", "https://misskon.com/tag/mistar/"),
                SourceCategory("Chinese:MTMeng", "https://misskon.com/tag/mtmeng/"),
                SourceCategory("Chinese:MyGirl", "https://misskon.com/tag/mygirl/"),
                SourceCategory("Chinese:PartyCat", "https://misskon.com/tag/partycat/"),
                SourceCategory("Chinese:QingDouKe", "https://misskon.com/tag/qingdouke/"),
                SourceCategory("Chinese:RuiSG", "https://misskon.com/tag/ruisg/"),
                SourceCategory("Chinese:SLADY", "https://misskon.com/tag/slady/"),
                SourceCategory("Chinese:TASTE", "https://misskon.com/tag/taste/"),
                SourceCategory("Chinese:TGOD", "https://misskon.com/tag/tgod/"),
                SourceCategory("Chinese:TouTiao", "https://misskon.com/tag/toutiao/"),
                SourceCategory("Chinese:TuiGirl", "https://misskon.com/tag/tuigirl/"),
                SourceCategory("Chinese:Tukmo", "https://misskon.com/tag/tukmo/"),
                SourceCategory("Chinese:UGIRLS", "https://misskon.com/tag/ugirls/"),
                SourceCategory("Chinese:UGIRLS - Ai You Wu App", "https://misskon.com/tag/ugirls-ai-you-wu-app/"),
                SourceCategory("Chinese:UXING", "https://misskon.com/tag/uxing/"),
                SourceCategory("Chinese:WingS", "https://misskon.com/tag/wings/"),
                SourceCategory("Chinese:XiaoYu", "https://misskon.com/tag/xiaoyu/"),
                SourceCategory("Chinese:XingYan", "https://misskon.com/tag/xingyan/"),
                SourceCategory("Chinese:XIUREN", "https://misskon.com/tag/xiuren/"),
                SourceCategory("Chinese:XR Uncensored", "https://misskon.com/tag/xr-uncensored/"),
                SourceCategory("Chinese:YouMei", "https://misskon.com/tag/youmei/"),
                SourceCategory("Chinese:YouMi", "https://misskon.com/tag/youmi/"),
                SourceCategory("Chinese:YouMi尤蜜", "https://misskon.com/tag/youmiapp/"),
                SourceCategory("Chinese:YouWu", "https://misskon.com/tag/youwu/"),
            )

            val KOREAN = listOf(
                SourceCategory("Korean:AG", "https://misskon.com/tag/ag/"),
                SourceCategory("Korean:Bimilstory", "https://misskon.com/tag/bimilstory/"),
                SourceCategory("Korean:BLUECAKE", "https://misskon.com/tag/bluecake/"),
                SourceCategory("Korean:CreamSoda", "https://misskon.com/tag/creamsoda/"),
                SourceCategory("Korean:DJAWA", "https://misskon.com/tag/djawa/"),
                SourceCategory("Korean:Espacia Korea", "https://misskon.com/tag/espacia-korea/"),
                SourceCategory("Korean:Fantasy Factory", "https://misskon.com/tag/fantasy-factory/"),
                SourceCategory("Korean:Fantasy Story", "https://misskon.com/tag/fantasy-story/"),
                SourceCategory("Korean:Glamarchive", "https://misskon.com/tag/glamarchive/"),
                SourceCategory("Korean:HIGH FANTASY", "https://misskon.com/tag/high-fantasy/"),
                SourceCategory("Korean:KIMLEMON", "https://misskon.com/tag/kimlemon/"),
                SourceCategory("Korean:KIREI", "https://misskon.com/tag/kirei/"),
                SourceCategory("Korean:KiSiA", "https://misskon.com/tag/kisia/"),
                SourceCategory("Korean:Korean Realgraphic", "https://misskon.com/tag/korean-realgraphic/"),
                SourceCategory("Korean:Lilynah", "https://misskon.com/tag/lilynah/"),
                SourceCategory("Korean:Lookas", "https://misskon.com/tag/lookas/"),
                SourceCategory("Korean:Loozy", "https://misskon.com/tag/loozy/"),
                SourceCategory("Korean:Moon Night Snap", "https://misskon.com/tag/moon-night-snap/"),
                SourceCategory("Korean:Paranhosu", "https://misskon.com/tag/paranhosu/"),
                SourceCategory("Korean:PhotoChips", "https://misskon.com/tag/photochips/"),
                SourceCategory("Korean:Pure Media", "https://misskon.com/tag/pure-media/"),
                SourceCategory("Korean:PUSSYLET", "https://misskon.com/tag/pussylet/"),
                SourceCategory("Korean:SAINT Photolife", "https://misskon.com/tag/saint-photolife/"),
                SourceCategory("Korean:SWEETBOX", "https://misskon.com/tag/sweetbox/"),
                SourceCategory("Korean:UHHUNG MAGAZINE", "https://misskon.com/tag/uhhung-magazine/"),
                SourceCategory("Korean:UMIZINE", "https://misskon.com/tag/umizine/"),
                SourceCategory("Korean:WXY ENT", "https://misskon.com/tag/wxy-ent/"),
                SourceCategory("Korean:Yo-U", "https://misskon.com/tag/yo-u/"),
            )

            val OTHER = listOf(
                SourceCategory("Other:AI Generated", "https://misskon.com/tag/ai-generated/"),
                SourceCategory("Other:Cosplay", "https://misskon.com/tag/cosplay/"),
                SourceCategory("Other:JP", "https://misskon.com/tag/jp/"),
                SourceCategory("Other:JVID", "https://misskon.com/tag/jvid/"),
                SourceCategory("Other:Patreon", "https://misskon.com/tag/patreon/"),
            )
        }

        fun create(): SourceCategorySelector {
            val options = mutableListOf(SourceCategory("unselected", "")).apply {
                addAll(TOP)
                addAll(CHINESE)
                addAll(KOREAN)
                addAll(OTHER)
            }
            return SourceCategorySelector("Category", options)
        }
    }
}
