import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BLKomik"
    theme = "wpcomics"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://blkomik.net"
        lang = "id"
    }

    deeplink {
        path("/manga/..*")
    }
}
