import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Lycan Toons"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://lycantoons.com"
    }

    deeplink {
        path("/series/..*")
    }
}
