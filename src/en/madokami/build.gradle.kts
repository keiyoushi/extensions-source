import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Madokami"
    versionCode = 16
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        lang = "en"
        baseUrl = "https://manga.madokami.al"
    }

    deeplink {
        path("/Manga/..*")
        path("/Raws/..*")
        path("/Artbooks/..*")
        path("/reader/..*")
    }
}

dependencies {
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")
}
