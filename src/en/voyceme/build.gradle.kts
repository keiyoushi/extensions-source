import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Voyce.Me"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    source {
        name = "VoyceMe"
        lang = "en"
        baseUrl = "https://www.voyce.me"
        id = 4815322300278778429L
    }
}
