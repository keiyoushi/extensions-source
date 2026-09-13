import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FOD"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "ja"
        baseUrl = "https://manga.fod.fujitv.co.jp"
    }
}
