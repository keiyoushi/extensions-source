import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "KumoPoi"
    versionCode = 2
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        name = "KumoPoi"
        lang = "id"
        baseUrl {
            custom("https://beta.kumopoi.com")
        }
    }

    deeplink {
        path("/comic/..*")
    }
}
