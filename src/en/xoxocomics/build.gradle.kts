import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "XOXO Comics"
    versionCode = 5
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "wpcomics"

    source {
        lang = "en"
        baseUrl = "https://xoxocomic.com"
    }
}
