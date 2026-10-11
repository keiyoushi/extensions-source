package eu.kanade.tachiyomi.extension.ja.hakusensha

import eu.kanade.tachiyomi.source.model.Filter

open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
    val value: String
        get() = vals[state].second
}

class ListFilter :
    SelectFilter(
        "一覧",
        arrayOf(
            "新着" to "product?type=new",
            "無料" to "product?type=free",
            "完結作品" to "group?type=complete",
            "完結作品：少女、女性コミック" to "group?type=complete&target=comic_girl_woman",
            "完結作品：少年、青年コミック" to "group?type=complete&target=comic_boy_man",
            "完結作品：ボーイズラブコミック" to "group?type=complete&target=comic_BL",
            "種類：少女、女性コミック" to "group?type=title&target=comic_girl_woman",
            "種類：少年、青年コミック" to "group?type=title&target=comic_boy_man",
            "種類：ボーイズラブコミック" to "group?type=title&target=comic_BL",
            "種類：グラビア写真集" to "group?type=title&target=comic_gravure",
            "種類：絵本" to "group?type=title&target=comic_picturebook",
            "種類：少女、女性コミック雑誌" to "group?type=title&target=comic_magazine_girl_woman",
            "種類：少年、青年コミック雑誌" to "group?type=title&target=comic_magazine_boy_man",
            "種類：ボーイズラブコミック雑誌" to "group?type=title&target=comic_magazine_BL",
            "掲載誌：花とゆめ" to "group?type=magazine&target=hanatoyume",
            "掲載誌：ザ花とゆめ" to "group?type=magazine&target=thehanatoyume",
            "掲載誌：花ゆめAi" to "group?type=magazine&target=hanayumeai",
            "掲載誌：Trifle by 花とゆめ" to "group?type=magazine&target=triflebyhanatoyume",
            "掲載誌：LaLa" to "group?type=magazine&target=lala",
            "掲載誌：異世界転生LaLa" to "group?type=magazine&target=isekaitenseilala",
            "掲載誌：××LaLa" to "group?type=magazine&target=xxlala",
            "掲載誌：LaLaDX" to "group?type=magazine&target=laladx",
            "掲載誌：メロディ" to "group?type=magazine&target=melody",
            "掲載誌：マンガPark" to "group?type=magazine&target=mangapark",
            "掲載誌：黒蜜" to "group?type=magazine&target=kuromitsu",
            "掲載誌：楽園" to "group?type=magazine&target=rakuen",
            "掲載誌：花丸漫画" to "group?type=magazine&target=hanamarumanga",
            "掲載誌：Love Jossie" to "group?type=magazine&target=lovejossie",
            "掲載誌：Love Silky" to "group?type=magazine&target=lovesilky",
            "掲載誌：ホラー シルキー" to "group?type=magazine&target=horrorsilky",
            "掲載誌：ヤングアニマル" to "group?type=magazine&target=younganimal",
            "ジャンル：ラブ" to "group?type=category&target=ラブ",
            "ジャンル：ファンタジー" to "group?type=category&target=ファンタジー",
            "ジャンル：学園" to "group?type=category&target=学園",
            "ジャンル：アクション（バトル）" to "group?type=category&target=アクション",
            "ジャンル：コメディ（ギャグ・エッセイ）" to "group?type=category&target=コメディ",
            "ジャンル：ミステリー（ホラー）" to "group?type=category&target=ミステリー",
            "ジャンル：ヒューマンドラマ（禁断）" to "group?type=category&target=ヒューマンドラマ",
            "ジャンル：スポーツ・音楽・芸能" to "group?type=category&target=スポーツ・音楽・芸能",
            "ジャンル：青春・友情" to "group?type=category&target=青春・友情",
            "ジャンル：感動モノ（ハートフル・ファミリー・動物）" to "group?type=category&target=感動モノ",
            "ジャンル：時代モノ" to "group?type=category&target=時代モノ",
            "ジャンル：SF" to "group?type=category&target=SF",
            "ジャンル：グルメ" to "group?type=category&target=グルメ",
            "ジャンル：グラビア写真集" to "group?type=category&target=グラビア写真集",
            "ジャンル：絵本" to "group?type=category&target=絵本",
            "ジャンル：BLコミック" to "group?type=category&target=BLコミック",
            "掲載開始年代：1980年代" to "group?type=year&target=1980",
            "掲載開始年代：1990年代" to "group?type=year&target=1990",
            "掲載開始年代：2000年代" to "group?type=year&target=2000",
            "掲載開始年代：2010年代" to "group?type=year&target=2010",
            "掲載開始年代：2020年代" to "group?type=year&target=2020",
        ),
    )
