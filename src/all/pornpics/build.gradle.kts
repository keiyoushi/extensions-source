import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PornPics"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    listOf("en", "zh", "es", "de", "fr", "it", "ru", "ja").forEach { langCode ->
        source {
            lang = langCode
            baseUrl = "https://www.pornpics.com"
            if (langCode == "en") id = 1459635082044256286L
        }
    }

    deeplink {
        path("/.*/galleries/..*")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
