import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hattori Manga"
    versionCode = 44
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "tr"
        baseUrl = "https://hattorimanga.net"
        versionId = 2
    }

    deeplink {
        host("hattorimanga.net")
        path("/manga/..*")
    }
}
