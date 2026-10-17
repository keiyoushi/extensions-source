import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Mangadusleri"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        versionId = 2
        lang = "tr"
        baseUrl {
            custom("https://mangadusleri.mom")
        }
    }

    deeplink {
        path("/manga/..*")
    }
}
