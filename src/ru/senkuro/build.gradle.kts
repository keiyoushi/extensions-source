import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Senkuro"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"
    theme = "senkuro"

    source {
        baseUrl {
            mirrors(
                "Россия" to "https://senkuro.me",
                "Публичный" to "https://senkuro.com",
            )
        }
        lang = "ru"
    }
}
