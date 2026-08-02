import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Voyce.Me"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "VoyceMe"
        lang = "en"
        baseUrl = "https://www.voyce.me"
        id = 4815322300278778429L
    }

    deeplink {
        path("/series/..*")
    }
}
