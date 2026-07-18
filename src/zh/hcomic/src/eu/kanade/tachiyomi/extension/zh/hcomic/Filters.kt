package eu.kanade.tachiyomi.extension.zh.hcomic

import eu.kanade.tachiyomi.source.model.Filter

class RandomFilter : Filter.Select<String>("排序", arrayOf("最近更新", "隨機排序")) {
    override fun toString() = arrayOf("", "/random")[state]
}

class TagGroup :
    Filter.Group<TagBox>(
        "人氣標籤",
        listOf(
            TagBox("全彩", "full color"),
            TagBox("巨乳", "big breasts"),
            TagBox("黑絲 / 白襪", "stockings"),
            TagBox("NTR", "netorare"),
            TagBox("足交 / 腳交", "footjob"),
            TagBox("女學生", "schoolgirl uniform"),
            TagBox("眼鏡控", "glasses"),
            TagBox("口交", "blowjob"),
            TagBox("正太控", "shotacon"),
            TagBox("亂倫", "incest"),
            TagBox("熟女 / 人妻", "milf"),
            TagBox("同志 BL", "yaoi"),
            TagBox("黑肉", "dark skin"),
            TagBox("泳裝", "swimsuit"),
            TagBox("手淫", "masturbation"),
            TagBox("肌肉", "muscle"),
            TagBox("姐姐 / 妹妹", "sister"),
            TagBox("捆綁", "bondage"),
            TagBox("調教", "femdom"),
            TagBox("催眠", "mind control"),
            TagBox("露出", "exhibitionism"),
            TagBox("群交", "group"),
            TagBox("肛交", "anal"),
            TagBox("獸交", "bestiality"),
        ),
    )

class TagBox(name: String, val value: String) : Filter.CheckBox(name)
