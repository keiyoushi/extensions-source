import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "AeroToon"
    versionCode = 1
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "tr"
        baseUrl = "https://aerotoon.vercel.app"
    }

    deeplink {
        path("/seri/..*")
        path("/series/..*")
        path("/tur/..*")
    }
}
