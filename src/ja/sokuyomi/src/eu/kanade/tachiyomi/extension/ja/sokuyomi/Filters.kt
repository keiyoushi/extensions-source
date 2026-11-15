package eu.kanade.tachiyomi.extension.ja.sokuyomi

import eu.kanade.tachiyomi.source.model.Filter

class TagFilter :
    SelectFilter(
        "タグで検索",
        arrayOf(
            Pair("ファンタジー", "bekjy7191h25mtofziehw4pyes10jlgh"),
            Pair("恋愛", "ad9eoi091zwomz650gssn2ioc1lmhq6j"),
            Pair("アクション", "n6dlagnw3wetf5v0xv3vgpc7vn3hsjle"),
            Pair("ロマンス", "pn6lqqayscod5s9vne32z0r52dohhdwb"),
            Pair("ヒューマンドラマ", "qegmk7mpj1xj884qn61esxvchxm40hc0"),
            Pair("学園", "py6h2fvi9oo3asia99bljz687koysiap"),
            Pair("ラブコメ", "0w5515dk908qk28kfr8e8sa5b80kdhn1"),
            Pair("ティーンズラブ", "h1vkbk952gm1p0biv9lb1im9jzdpcj1s"),
            Pair("BL", "hjeetas4c09fgpcse5wkxa0pfhcleme0"),
            Pair("アニメ化", "uchal80cpytiyhzf0vl5ifb5qf2t09n3"),
            Pair("職業・業界", "gc21jfb3z62bcxjq3m84wncxboz9d2sb"),
            Pair("異世界・転生", "kzrtzyo46ws9a6m90t1w2h33cn4s3wwg"),
            Pair("メディア化", "fjbw27uvqmt3ybwdtsw8lbji7ocy8r93"),
            Pair("殿堂入り", "f7qqmniifu4ryy04b19wzoy5bbkmqaxu"),
            Pair("コメディ", "3hxy4692q60yz43lpqad6a7vit8miaao"),
            Pair("ミステリー", "4i8nn0ncwwkxlkwbx4t38ff9xsz46vox"),
            Pair("スポーツ", "dtjgd6nwyexirj25trnxl2i64enl8e4a"),
            Pair("歴史", "0rx7ql4di5jekuoo7r3viiaj3tamgq1a"),
            Pair("サスペンス", "k9ei4510gkobvvd8joarj3tkcqre2ywl"),
            Pair("エッチ", "lejziydrqwg40q83f1sb8cwcmuag2qf4"),
        ),
    )

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}
