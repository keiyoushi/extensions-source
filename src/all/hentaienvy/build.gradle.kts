import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiEnvy"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"
    theme = "galleryadults"
    
    source {
        lang = "en"
        baseUrl = "https://hentaienvy.com"
    }
}
