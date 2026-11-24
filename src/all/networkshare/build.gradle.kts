import io.github.keiyoushi.gradle.api.ContentWarning

plugins {
    alias(kei.plugins.extension)
}

keiyoushi {
    name = "Network Share"
    versionCode = 0
    contentWarning = ContentWarning.SAFE
    libVersion = "1.6"

    source {
        name = "Network Share"
        lang = "all"
        baseUrl = "smb://127.0.0.1"
    }
    source {
        name = "Network Share (2)"
        lang = "all"
        baseUrl = "smb://127.0.0.1"
    }
    source {
        name = "Network Share (3)"
        lang = "all"
        baseUrl = "smb://127.0.0.1"
    }
}

android {
    packaging {
        // BouncyCastle's data files (PQC tables, X.509 messages); smbj uses none of them
        resources.excludes += "org/bouncycastle/**"
    }
}

dependencies {
    implementation("com.hierynomus:smbj:0.15.0")
    implementation("com.github.gpanther:java-nat-sort:natural-comparator-1.1")
}
