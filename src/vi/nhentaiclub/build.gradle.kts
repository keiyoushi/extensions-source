import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NhentaiClub"
    versionCode = 4
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl = "https://nhentaiclub.online"
        id = 9124366814387777661L
    }

    deeplink {
        path("/g/..*")
        path("/read/..*")
    }
}
