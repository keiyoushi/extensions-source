import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "TIBIU Comics"
    theme = "mccms"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "TIBIU漫画"
        lang = "zh"
        baseUrl = "https://comic.tibiu.net"
    }

    deeplink {
        path("/comic/..*")
        path("/chapter/..*")
    }
}
