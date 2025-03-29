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
                SourceCategory("Top 7 days", "/top7/"),
                SourceCategory("Top 30 days", "/top30/"),
                SourceCategory("Top 60 days", "/top60/"),
            )

            val CHINESE = listOf(
                SourceCategory("Chinese:[MTCos] 喵糖映画", "/tag/mtcos/"),
                SourceCategory("Chinese:BoLoli", "/tag/bololi/"),
                SourceCategory("Chinese:CANDY", "/tag/candy/"),
                SourceCategory("Chinese:FEILIN", "/tag/feilin/"),
                SourceCategory("Chinese:FToow", "/tag/ftoow/"),
                SourceCategory("Chinese:GIRLT", "/tag/girlt/"),
                SourceCategory("Chinese:HuaYan", "/tag/huayan/"),
                SourceCategory("Chinese:HuaYang", "/tag/huayang/"),
                SourceCategory("Chinese:IMISS", "/tag/imiss/"),
                SourceCategory("Chinese:ISHOW", "/tag/ishow/"),
                SourceCategory("Chinese:JVID", "/tag/jvid/"),
                SourceCategory("Chinese:KelaGirls", "/tag/kelagirls/"),
                SourceCategory("Chinese:Kimoe", "/tag/kimoe/"),
                SourceCategory("Chinese:LegBaby", "/tag/legbaby/"),
                SourceCategory("Chinese:MF", "/tag/mf/"),
                SourceCategory("Chinese:MFStar", "/tag/mfstar/"),
                SourceCategory("Chinese:MiiTao", "/tag/miitao/"),
                SourceCategory("Chinese:MintYe", "/tag/mintye/"),
                SourceCategory("Chinese:MISSLEG", "/tag/missleg/"),
                SourceCategory("Chinese:MiStar", "/tag/mistar/"),
                SourceCategory("Chinese:MTMeng", "/tag/mtmeng/"),
                SourceCategory("Chinese:MyGirl", "/tag/mygirl/"),
                SourceCategory("Chinese:PartyCat", "/tag/partycat/"),
                SourceCategory("Chinese:QingDouKe", "/tag/qingdouke/"),
                SourceCategory("Chinese:RuiSG", "/tag/ruisg/"),
                SourceCategory("Chinese:SLADY", "/tag/slady/"),
                SourceCategory("Chinese:TASTE", "/tag/taste/"),
                SourceCategory("Chinese:TGOD", "/tag/tgod/"),
                SourceCategory("Chinese:TouTiao", "/tag/toutiao/"),
                SourceCategory("Chinese:TuiGirl", "/tag/tuigirl/"),
                SourceCategory("Chinese:Tukmo", "/tag/tukmo/"),
                SourceCategory("Chinese:UGIRLS", "/tag/ugirls/"),
                SourceCategory("Chinese:UGIRLS - Ai You Wu App", "/tag/ugirls-ai-you-wu-app/"),
                SourceCategory("Chinese:UXING", "/tag/uxing/"),
                SourceCategory("Chinese:WingS", "/tag/wings/"),
                SourceCategory("Chinese:XiaoYu", "/tag/xiaoyu/"),
                SourceCategory("Chinese:XingYan", "/tag/xingyan/"),
                SourceCategory("Chinese:XIUREN", "/tag/xiuren/"),
                SourceCategory("Chinese:XR Uncensored", "/tag/xr-uncensored/"),
                SourceCategory("Chinese:YouMei", "/tag/youmei/"),
                SourceCategory("Chinese:YouMi", "/tag/youmi/"),
                SourceCategory("Chinese:YouMi尤蜜", "/tag/youmiapp/"),
                SourceCategory("Chinese:YouWu", "/tag/youwu/"),
            )

            val KOREAN = listOf(
                SourceCategory("Korean:AG", "/tag/ag/"),
                SourceCategory("Korean:Bimilstory", "/tag/bimilstory/"),
                SourceCategory("Korean:BLUECAKE", "/tag/bluecake/"),
                SourceCategory("Korean:CreamSoda", "/tag/creamsoda/"),
                SourceCategory("Korean:DJAWA", "/tag/djawa/"),
                SourceCategory("Korean:Espacia Korea", "/tag/espacia-korea/"),
                SourceCategory("Korean:Fantasy Factory", "/tag/fantasy-factory/"),
                SourceCategory("Korean:Fantasy Story", "/tag/fantasy-story/"),
                SourceCategory("Korean:Glamarchive", "/tag/glamarchive/"),
                SourceCategory("Korean:HIGH FANTASY", "/tag/high-fantasy/"),
                SourceCategory("Korean:KIMLEMON", "/tag/kimlemon/"),
                SourceCategory("Korean:KIREI", "/tag/kirei/"),
                SourceCategory("Korean:KiSiA", "/tag/kisia/"),
                SourceCategory("Korean:Korean Realgraphic", "/tag/korean-realgraphic/"),
                SourceCategory("Korean:Lilynah", "/tag/lilynah/"),
                SourceCategory("Korean:Lookas", "/tag/lookas/"),
                SourceCategory("Korean:Loozy", "/tag/loozy/"),
                SourceCategory("Korean:Moon Night Snap", "/tag/moon-night-snap/"),
                SourceCategory("Korean:Paranhosu", "/tag/paranhosu/"),
                SourceCategory("Korean:PhotoChips", "/tag/photochips/"),
                SourceCategory("Korean:Pure Media", "/tag/pure-media/"),
                SourceCategory("Korean:PUSSYLET", "/tag/pussylet/"),
                SourceCategory("Korean:SAINT Photolife", "/tag/saint-photolife/"),
                SourceCategory("Korean:SWEETBOX", "/tag/sweetbox/"),
                SourceCategory("Korean:UHHUNG MAGAZINE", "/tag/uhhung-magazine/"),
                SourceCategory("Korean:UMIZINE", "/tag/umizine/"),
                SourceCategory("Korean:WXY ENT", "/tag/wxy-ent/"),
                SourceCategory("Korean:Yo-U", "/tag/yo-u/"),
            )

            val OTHER = listOf(
                SourceCategory("Other:AI Generated", "/tag/ai-generated/"),
                SourceCategory("Other:Cosplay", "/tag/cosplay/"),
                SourceCategory("Other:JP", "/tag/jp/"),
                SourceCategory("Other:JVID", "/tag/jvid/"),
                SourceCategory("Other:Patreon", "/tag/patreon/"),
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
