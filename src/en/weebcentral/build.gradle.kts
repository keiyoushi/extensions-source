import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Weeb Central"
    versionCode = 22
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "en"
        baseUrl = "https://weebcentral.com"
    }

    deeplink {
        host("weebcentral.com")
        path("/series/..*")
    }
}
