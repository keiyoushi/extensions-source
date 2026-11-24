import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Pawchive"
    versionCode = 7
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://pawchive.pw"
    }
    deeplink {
        path("/patreon/user/..*")
        path("/fanbox/user/..*")
    }
}
