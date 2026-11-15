import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Manhwa18Dash"
    versionCode = 1
    contentWarning = ContentWarning.NSFW
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://manhwa18dash.com"
    }

    deeplink {
        host("manhwa18dash.com")
        host("www.manhwa18dash.com")
        path("/manga/..*")
        path("/genre/..*")
        path("/manhwa")
        path("/reader.php")
    }
}
