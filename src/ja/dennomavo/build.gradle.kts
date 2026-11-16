import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Denno Mavo"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        name = "電脳マヴォ"
        lang = "ja"
        baseUrl = "https://mavo.takekuma.jp"
    }

    deeplink {
        path("/title.php")
        path("/viewer.php")
        path("/pcviewer.php")
    }
}
