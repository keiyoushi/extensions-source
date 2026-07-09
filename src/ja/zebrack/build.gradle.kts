plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Zebrack"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://zebrack-comic.shueisha.co.jp"
    }
}
