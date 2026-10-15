import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Kill Six Billion Demons"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "KillSixBillionDemons"
        lang = "en"
        baseUrl = "https://killsixbilliondemons.com"
    }
}
