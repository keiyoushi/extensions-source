import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "NoyAcg"
    versionCode = 5
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh"
        baseUrl = "https://beta.noyteam.online"
    }

    deeplink {
        host("beta.noyteam.online")
        path("/manga/..*")
    }
}
