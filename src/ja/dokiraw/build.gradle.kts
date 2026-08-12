import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Dokiraw"
    versionCode = 7
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "liliana"

    source {
        lang = "ja"
        baseUrl {
            custom("https://dokiraw.beer")
        }
    }
}
