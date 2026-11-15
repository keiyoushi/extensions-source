package eu.kanade.tachiyomi.extension.zh.zazhimi

import eu.kanade.tachiyomi.source.model.Filter

class TypeFilter :
    Filter.Select<String>(
        "分类",
        arrayOf("女装服饰", "美妆美发", "时尚男士", "娱乐明星", "手工制作", "居家生活"),
    ) {
    override fun toString() = arrayOf("6", "8", "9", "10", "127", "168")[state]
}

class BrandFilter :
    Filter.Select<String>(
        "品牌",
        arrayOf(
            "全部", "BeasUp", "美的", "VoCE", "MAQUIA", "nail venus",
            "ageha", "NAIL MAX", "Nail Up", "ar", "TOMOTOMO",
            "CHOKiCHOKi", "springヘア&ビューティー", "美ST", "LDK the Beauty",
            "preppy", "Nail Ex", "HAIR MODE", "其他美容美甲画册",
        ),
    ) {
    override fun toString(): String = arrayOf(
        "", "36", "48", "51", "53", "65", "84",
        "109", "121", "149", "155", "178",
        "214", "357", "361", "386", "395",
        "400", "349",
    )[state]
}
