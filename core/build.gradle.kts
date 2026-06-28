plugins {
    // استخدم إضافات keiyoushi فقط، لا تكرر إضافة android.library يدوياً إذا كانت موجودة في keiyoushi
    alias(libs.plugins.kotlin.serialization)
    alias(kei.plugins.android.base)
    alias(kei.plugins.spotless)
}

android {
    namespace = "keiyoushi.core"

    buildFeatures {
        resValues = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // إزالة implementation(project(":core")) لأنه لا يمكن للمكتبة أن تعتمد على نفسها
    // تأكد أن madara لا تعتمد أيضاً على core في ملفها الخاص
    implementation(project(":lib-multisrc:madara"))

    compileOnly(libs.bundles.common)
    testImplementation(libs.bundles.common)
    testImplementation(libs.junit)
}
