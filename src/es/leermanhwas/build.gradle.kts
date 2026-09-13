import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "LeerManhwas"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        baseUrl = "https://leermanhwas.com"
        lang = "es"
    }

    deeplink {
        path("/..*")
    }
}
