import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pornhwa18"
    pkgName = "id.pornhwa18"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://pornhwa18.com"
        versionId = 2
    }

    deeplink {
        path("/comic/..*")
    }
}
