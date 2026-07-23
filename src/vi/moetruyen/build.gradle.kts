import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "MoeTruyen"
    versionCode = 8
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    deeplink {
        path("/manga/.*")
    }

    source {
        lang = "vi"
        baseUrl {
            mirrors(
                "https://moetruyen.net",
                "https://truyen.moe",
            )
        }
    }
}
