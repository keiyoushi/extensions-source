import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "DreamTeams Scans"
    versionCode = 0
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "loneseal"

    source {
        lang = "id"
        baseUrl = "https://dreamteams.space"
    }
}
