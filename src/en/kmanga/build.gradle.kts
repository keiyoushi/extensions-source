import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "K Manga"
    versionCode = 6
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://kmanga.kodansha.com"
    }

    deeplink {
        host("kmanga.kodansha.com")
        path("/title/..*")
    }
}
