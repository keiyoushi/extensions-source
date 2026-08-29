import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MyComic"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "zh"
        baseUrl = "https://mycomic.com"
    }
}
