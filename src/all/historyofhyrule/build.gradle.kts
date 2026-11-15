import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "History of Hyrule"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "all"
        baseUrl = "https://historyofhyrule.com"
    }

    deeplink {
        host("historyofhyrule.com")
        host("www.historyofhyrule.com")
        path("/publications/..*")
        path("/index.php")
    }
}
