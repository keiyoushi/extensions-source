import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HotComics"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    deeplink {
        path("/en/..*/..*\\.html")
    }

    source {
        lang = "en"
        baseUrl = "https://hotcomics.io"
    }
}
