import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Com-X"
    versionCode = 42
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl {
            mirrors(
                "Россия" to "https://ru.com-x.life",
                "Публичный" to "https://com-x.life",
            )
        }
        lang = "ru"
        id = 1114173092141608635L
    }

    deeplink {
        path("/..*")
    }
}
