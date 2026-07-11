import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hiperdex"
    versionCode = 80
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "hiper"

    source {
        lang = "en"
        baseUrl {
            custom("https://hiperdex.com")
        }
    }
}
