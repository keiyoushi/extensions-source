import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "BookWalker Taiwan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "zh-Hant"
        baseUrl = "https://www.bookwalker.com.tw"
    }

    deeplink {
        host("www.bookwalker.com.tw")
        host("bookwalker.com.tw")
        path("/product/..*")
        path("/serial/..*")
        path("/browserViewer/..*")
        path("/search")
    }
}

dependencies {
    implementation(project(":lib:publus"))
}
