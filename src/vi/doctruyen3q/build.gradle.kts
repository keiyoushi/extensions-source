import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DocTruyen3Q"
    versionCode = 30
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://doctruyen3qhub.vip")
        }
    }
}
