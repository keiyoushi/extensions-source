import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "WebtoonClub"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        baseUrl = "https://webtoonclub.com"
        lang = "en"
    }

    deeplink {
        path("/webtoon/..*")
        path("/webtoons")
        path("/genre/..*")
        path("/author/..*")
    }
}
