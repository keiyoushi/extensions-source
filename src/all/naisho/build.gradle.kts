import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Naisho"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://naisho.moe"
    }

    deeplink {
        path("/g")
        path("/g/..*")
        path("/home")
        path("/trending")
    }
}
