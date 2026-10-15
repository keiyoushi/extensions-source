package eu.kanade.tachiyomi.multisrc.loneseal

private val htmlTags = """<[^>]*>""".toRegex()
private val words = """[A-Za-z]+""".toRegex()

private val fillerWords = setOf(
    "adalah", "akan", "akankah", "akhirnya", "always", "antara", "apakah", "atas", "atau", "author", "baca", "bagai",
    "bagaimana", "bagi", "bahkan", "bahwa", "banyak", "baru", "before", "begins", "begitu", "beli", "belum", "benar",
    "berada", "bernama", "bertemu", "besar", "biasa", "bisa", "bukan", "cantik", "cara", "chapter", "chapters", "com",
    "comic", "commotion", "dalam", "dan", "dapat", "dapatkan", "dari", "datang", "death", "dengan", "depan", "dewa",
    "dia", "diam", "dimana", "diri", "diriku", "dirinya", "dunia", "emotion", "enam", "episode", "feel", "filled",
    "gadis", "game", "gmail", "haeilnox", "hampir", "hanya", "hari", "harus", "hidup", "hidupmu", "hidupnya", "high",
    "href", "http", "https", "ingin", "ini", "isi", "itu", "jadi", "jika", "jiwa", "juga", "junior", "justru",
    "karena", "kecil", "kedua", "kehidupan", "kekuatan", "kelas", "kembali", "kemudian", "kenyataan", "kepada",
    "ketika", "kini", "kisah", "klik", "komik", "kuat", "lagi", "lain", "laki", "lalu", "langsung", "lebih", "life",
    "link", "list", "longer", "luka", "mailto", "maka", "malam", "mana", "manga", "manusia", "masa", "masih", "masing",
    "mau", "melainkan", "melakukan", "melihat", "memang", "membuat", "memiliki", "mendapatkan", "mengenai",
    "menggunakan", "menjadi", "menjalani", "mereka", "merupakan", "monster", "mulai", "muncul", "mungkin", "murid",
    "mysterious", "nama", "namun", "naver", "nothing", "oleh", "online", "orang", "original", "pada", "paling", "para",
    "pemuda", "pernah", "read", "romance", "saat", "saja", "salah", "saling", "sama", "sampai", "sang", "sangat",
    "satu", "satunya", "saya", "school", "sebagai", "sebelumnya", "sebuah", "secara", "sedang", "sedangkan", "segala",
    "sehingga", "sejak", "sekadar", "sekaligus", "sekarang", "sekolah", "selalu", "selama", "seluruh", "semakin",
    "semua", "semuanya", "senior", "seorang", "seperti", "series", "serta", "sesuatu", "setelah", "setiap", "seumur",
    "si", "sinopsis", "sistem", "story", "student", "studio", "subjek", "sudah", "tahun", "tangan", "tanpa", "tapi",
    "telah", "teman", "tempat", "tengah", "tentang", "terhadap", "terlalu", "tersebut", "tersisa", "tetapi",
    "thriller", "tiba", "tidak", "title", "tubuh", "tulisan", "under", "untuk", "update", "video", "waktu", "wanita",
    "webtoon", "webtoons", "with", "www", "yang", "year",
)

private class Candidate(val value: String, val occurrences: Int, val common: Boolean, val firstIndex: Int)

internal fun String.relatedKeywords(): List<String> {
    val prose = htmlTags.replace(this, " ")
    val scanned = words.findAll(prose).map { it.range.first to it.value }.toList()

    val candidates = scanned
        .filter { (_, word) -> word.length >= 4 }
        .filterNot { (_, word) -> word.all(Char::isUpperCase) }
        .filterNot { (_, word) -> word.lowercase() in fillerWords }
        .groupBy { (_, word) -> word.lowercase() }
        .map { (_, occurrences) -> Candidate(value = occurrences.first().second, occurrences = occurrences.size, common = occurrences.any { (_, word) -> word.first().isLowerCase() }, firstIndex = occurrences.first().first) }

    return candidates
        .sortedWith(compareByDescending<Candidate> { it.common }.thenByDescending { it.occurrences }.thenBy { it.firstIndex })
        .map { it.value }
}
