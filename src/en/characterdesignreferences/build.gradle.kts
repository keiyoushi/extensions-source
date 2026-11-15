import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Character Design References"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://characterdesignreferences.com"
    }

    deeplink {
        path("/art-of-animation-.*/..*")
        path("/artists-of-the-week/..*")
        path("/artist-of-the-week-.*/..*")
        path("/blog-interviews.*/..*")
        path("/winners.*/..*")
        path("/challengers-spotlights/..*")
    }
}
