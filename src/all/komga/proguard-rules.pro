# Keep class names for reflection (qualifiedName)
-keepnames class eu.kanade.tachiyomi.extension.all.komga.Komga

# Keep SSL/TLS classes used by mTLS
-keepclassmembers class * extends javax.net.ssl.X509KeyManager {
    public *;
}
-keep class javax.net.ssl.KeyManagerFactory { *; }
-keep class javax.net.ssl.SSLContext { *; }
