import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "PDF Toshokan"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://pdftoshokan.com"
        lang = "ja"
    }

    deeplink {
        path("/manga/..*")
    }
}
