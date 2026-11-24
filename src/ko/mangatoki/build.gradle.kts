import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangatoki"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "망가토끼"
        lang = "ko"
        baseUrl {
            custom("https://mg.mangatoki2.com")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
