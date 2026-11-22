import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NameeToon"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "나미툰"
        lang = "ko"
        baseUrl {
            custom("https://namee134.asia")
        }
    }

    deeplink {
        path("/list.php")
    }
}
