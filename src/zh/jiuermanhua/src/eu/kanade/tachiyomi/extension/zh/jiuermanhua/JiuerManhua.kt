package eu.kanade.tachiyomi.extension.zh.jiuermanhua

import eu.kanade.tachiyomi.multisrc.sinmh.SinMH
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source

@Source
abstract class JiuerManhua : SinMH() {

    override val mobileUrl = "http://h5.92mh.com"

    override fun pageListUrl(chapter: SChapter) = baseUrl + chapter.url
}
