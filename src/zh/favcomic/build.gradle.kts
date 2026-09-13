import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "FavComic"
    versionCode = 4
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "喜漫漫画"
        lang = "zh"
        baseUrl = "https://www.favcomic.com"
    }
}
