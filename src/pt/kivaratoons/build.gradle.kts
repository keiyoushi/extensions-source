import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KivaraToons"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://kivaratoons.com"
    }

    deeplink {
        host("kivaratoons.com")
        path("/obra/..*")
        path("/manhwa/..*")
        path("/reader/..*")
    }
}
