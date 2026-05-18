import keiyoushi.gradle.extensions.baseVersionCode

plugins {
    alias(kei.plugins.multisrc)
}

baseVersionCode = 21

dependencies {
    api(project(":lib:i18n"))
}
