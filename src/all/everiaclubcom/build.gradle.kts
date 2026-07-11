import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "EveriaClub (unoriginal)"
    versionCode = 1
    contentWarning = ContentWarning.NSFW // or MIXED, please confirm
    libVersion = "1.4"

    source {
        lang = "all"
        baseUrl = "https://www.everiaclub.com"
    }
}
