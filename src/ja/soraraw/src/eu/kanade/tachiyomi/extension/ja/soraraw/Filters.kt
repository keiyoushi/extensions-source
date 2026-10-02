package eu.kanade.tachiyomi.extension.ja.soraraw

import eu.kanade.tachiyomi.source.model.Filter

class ContentFilter : Filter.Select<String>("コンテンツ", arrayOf("すべて", "一般", "18+"))

class ModeFilter : Filter.Select<String>("表示モード", arrayOf("すべて", "縦", "横"))

class StatusFilter : Filter.Select<String>("ステータス", arrayOf("すべて", "連載中", "完結"))

class SortFilter : Filter.Select<String>("並び順", arrayOf("閲覧数", "更新", "保存"))

class GenreFilter :
    Filter.Select<String>(
        "ジャンル",
        arrayOf(
            "All",
            "日本漫画",
            "ファンタジー",
            "コメディ",
            "ロマンス",
            "アクション",
            "ドラマ",
            "青年",
            "冒険",
            "少年",
            "日常",
            "フルカラー",
            "恋愛",
            "学園生活",
            "SMARTOON",
            "超自然",
            "ハーレム",
            "独占配信",
            "少女",
            "異世界",
            "更新中",
            "オリジナル",
            "ミステリー",
            "女性マンガ",
            "ホラー",
            "歴史",
            "転生",
            "SF",
            "心理",
            "青年マンガ",
            "成熟",
            "女性",
            "ラブコメ",
            "コミカライズ",
            "スポーツ",
            "学園",
            "少女マンガ",
            "少年マンガ",
            "現代",
            "ギャグ・コメディ",
            "完結",
            "王様・貴族",
            "悲劇",
            "復讐",
            "胸キュン",
            "異能力",
            "ラブストーリー",
            "魔法",
            "百合",
        ),
    )
