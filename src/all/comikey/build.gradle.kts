plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Comikey"
    versionCode = 7
    contentWarning = ContentWarning.SAFE
    libVersion = "1.4"

    listOf("en", "es", "id", "pt-BR").forEach {
        source {
            lang = it
            baseUrl = "https://comikey.com"
        }
    }
    source {
        name = "Comikey Brasil"
        lang = "pt-BR"
        baseUrl = "https://br.comikey.com"
    }

    deeplink {
        host("comikey.com")
        host("br.comikey.com")
        path("/comics/..*/..*/")
    }
}

dependencies {

    implementation(project(":lib:i18n"))
}
