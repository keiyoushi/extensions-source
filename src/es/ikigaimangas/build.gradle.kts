import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Ikigai Mangas"
    versionCode = 34
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"

    source {
        lang = "es"
        baseUrl {
            custom("https://zonaikigai.gamesview.shop")
        }
        versionId = 2
    }
}
