import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "HentaiNexus"
    versionCode = 20
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://hentainexus.com"
    }

    deeplink {
        host("hentainexus.com")
        path("/view/..*")
    }
}
