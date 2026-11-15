import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Myth Toons"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://mythtoons.org"
    }

    deeplink {
        host("mythtoons.org")
        host("www.mythtoons.org")
        path("/manga/..*")
        path("/read/..*")
    }
}
