package eu.kanade.tachiyomi.extension.zh.qimanwu

import eu.kanade.tachiyomi.multisrc.mccms.GenreFilter
import eu.kanade.tachiyomi.multisrc.mccms.Intl
import eu.kanade.tachiyomi.multisrc.mccms.MCCMSFilter
import eu.kanade.tachiyomi.multisrc.mccms.WebStatusFilter
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList

class SortFilter : MCCMSFilter(Intl.sort, arrayOf(Intl.popular, Intl.latest), arrayOf("order/hits", "order/addtime"))

class RegionFilter :
    MCCMSFilter(
        "受众",
        arrayOf(Intl.all, "国产漫画", "日本漫画", "韩国漫画", "欧美漫画"),
        arrayOf("", "list/1", "list/2", "list/3", "list/4"),
    )

// In the site's own path order, which it redirects other orders to
fun getFilters(genres: List<Pair<String, String>>?): FilterList = FilterList(
    buildList {
        add(Filter.Header(Intl.categoryWeb))
        add(RegionFilter())
        if (!genres.isNullOrEmpty()) add(GenreFilter(genres).webFilter)
        add(WebStatusFilter())
        add(SortFilter())
    },
)
