import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Tongli"
    versionCode = 0
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "東立"
        lang = "zh"
        baseUrl = "https://ebook.tongli.com.tw"
    }

    deeplink {
        path("/book")
    }
}
