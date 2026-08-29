import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DragonTranslation.org"
    versionCode = 2
    contentWarning = ContentWarning.MIXED
    libVersion = "1.4"
    theme = "madaralegacy"

    source {
        lang = "es"
        baseUrl = "https://dragontranslation.org"
    }
}
