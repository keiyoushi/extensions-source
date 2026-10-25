import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "INKRUSH"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://inkrush.site"
    }

    source {
        lang = "id"
        baseUrl = "https://inkrush.site"
    }

    deeplink {
        path("/comics/..*")
    }
}
