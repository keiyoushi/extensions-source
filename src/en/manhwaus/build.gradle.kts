import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "ManhwaUS"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "yiimadara"

    source {
        lang = "en"
        baseUrl = "https://manhwaus.net"
    }

    deeplink {
        path("/webtoon/..*")
    }
}
