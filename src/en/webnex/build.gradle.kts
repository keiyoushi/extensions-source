import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Webnex"
    versionCode = 3
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://webnex.cc"
    }

    deeplink {
        path("/manga/..*")
        path("/read/..*")
        path("/browse")
        path("/groups/..*")
    }
}
