import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Hentai-Scantrad"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"
    theme = "madara"

    source {
        lang = "fr"
        baseUrl = "https://hentai.scantrad-vf.cc"
    }
}
