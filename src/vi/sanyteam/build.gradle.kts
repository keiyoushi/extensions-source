import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Sany Team"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "vi"
        baseUrl = "https://teamsany.com"
    }

    deeplink {
        host("teamsany.com")
        host("www.teamsany.com")
        path("/..*")
    }
}
