import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Cutie Comics"
    versionCode = 5
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://cutiecomics.com"
    }

    deeplink {
        host("cutiecomics.com")
        path("/..*")
    }
}
