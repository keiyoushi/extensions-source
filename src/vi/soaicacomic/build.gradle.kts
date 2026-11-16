import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "SoaiCaComic"
    versionCode = 10
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "koi"

    source {
        lang = "vi"
        baseUrl {
            custom("https://soaicacomic2.top")
        }
    }
}
