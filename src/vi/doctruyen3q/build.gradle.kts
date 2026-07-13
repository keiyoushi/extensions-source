import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DocTruyen3Q"
    versionCode = 28
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "vi"
        baseUrl {
            custom("https://doctruyen3qhub3.com")
        }
    }
}
