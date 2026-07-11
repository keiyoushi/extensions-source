import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiArchive"
    versionCode = 3
    contentWarning = ContentWarning.NSFW
    libVersion = "1.4"

    source {
        lang = "it"
        baseUrl = "https://www.hentai-archive.com"
    }
}
