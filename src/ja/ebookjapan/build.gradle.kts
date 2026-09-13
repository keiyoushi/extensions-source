import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "eBookJapan"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://ebookjapan.yahoo.co.jp"
    }
}
