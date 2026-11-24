import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Yurei Scan"
    versionCode = 1
    contentWarning = ContentWarning.MIXED
    libVersion = "1.6"

    source {
        lang = "fr"
        baseUrl = "https://yurei-scan.fr"
    }

    deeplink {
        host("yurei-scan.fr")
        host("www.yurei-scan.fr")
        path("/manga.php")
        path("/manga")
        path("/chapter.php")
        path("/chapter")
    }
}
