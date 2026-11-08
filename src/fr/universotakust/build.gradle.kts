import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Univers Otaku (unoriginal)"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "crunchyscanst"

    source {
        lang = "fr"
        baseUrl = "https://univers-otaku.st"
    }
}
