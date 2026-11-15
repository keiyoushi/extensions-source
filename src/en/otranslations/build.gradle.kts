import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "O Translations"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        baseUrl = "https://otl.moe"
        lang = "en"
    }

    deeplink {
        path("/series/..*")
        path("/read/..*")
    }
}
