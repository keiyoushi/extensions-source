import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Blitz Toons"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "pt-BR"
        baseUrl = "https://blitztoons.com"
    }

    deeplink {
        host("blitztoons.com")
        host("www.blitztoons.com")
        path("/obra/..*")
        path("/ler/..*")
        path("/obras")
    }
}
