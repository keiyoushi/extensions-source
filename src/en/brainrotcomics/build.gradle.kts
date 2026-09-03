import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BrainRotComics"
    pkgName = "en.aryascans"
    versionCode = 3
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"
    theme = "madara"

    source {
        lang = "en"
        baseUrl = "https://brainrotcomics.com"
        id = 7621724834621233483
    }
}
