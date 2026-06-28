plugins {
    id("com.android.library")
    alias(kei.plugins.multisrc)
}

android {
    // هذا هو المعرف الفريد لمكتبتك، تأكد ألا يتكرر في أي module آخر
    namespace = "keiyoushi.multisrc.madara" 
    
    // يجب أن يطابق compileSdk الموجود في ملف build.gradle الرئيسي للمشروع
    compileSdk = 35 

    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    api(project(":lib:cryptoaes"))
    api(project(":lib:i18n"))
}

// هذا هو الجزء الذي كان يظهر في كودك الأصلي، تأكد من وجوده
keiyoushi {
    baseVersionCode = 51
    libVersion = "1.4"

    deeplink {
        path("/.*/..*")
    }
}
