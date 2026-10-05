# ==========================================================
# GŁÓWNE REGUŁY ANDROIDA
# ==========================================================
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Zachowaj klasy implementujące Parcelable (wymagane przez system Android)
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ==========================================================
# KOIN (Dependency Injection)
# ==========================================================
-keep class org.koin.** { *; }
-keepnames class * extends org.koin.core.module.Module

# Koin w Androidzie mocno opiera się na ViewModelach
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# ==========================================================
# KTOR (Networking)
# ==========================================================
-keep class io.ktor.** { *; }
-keep class * implements io.ktor.client.engine.HttpClientEngine { *; }
-keep class * implements io.ktor.client.engine.HttpClientEngineFactory { *; }

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**

# ==========================================================
# KOTLINX SERIALIZATION
# ==========================================================
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.** { *; }

# ==========================================================
# ROOM (Baza danych) & SQLITE (Android)
# ==========================================================
# Wymagane, aby R8 nie usunął wygenerowanych implementacji bazy danych
-keep class androidx.room.RoomDatabase.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Room pod Androidem korzysta z androidx.sqlite
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**
-dontwarn androidx.room.**

# ==========================================================
# KERMIT (Logging)
# ==========================================================
-keep class co.touchlab.kermit.** { *; }
-dontwarn co.touchlab.kermit.**

# ==========================================================
# TWOJE KLASY BIZNESOWE I MODELE (BARDZO WAŻNE)
# ==========================================================
# Zachowaj struktury klas, które wstrzykujesz przez Koina lub używasz w JSON/Bazie
-keep class pl.misek.toafmobile.network.** { *; }
-keep class pl.misek.toafmobile.database.** { *; }
-keep class pl.misek.toafmobile.modules.interfaces.** { *; }
-keep class pl.misek.toafmobile.coding.** { *; }
-keep class pl.misek.toafmobile.control.** { *; }
-keep class pl.misek.toafmobile.protocols.** { *; }
-keep class pl.misek.toafmobile.interfaces.** { *; }
-keep class pl.misek.toafmobile.viewModels.** { *; }

# ==========================================================
# USB SERIAL FOR ANDROID (Zastępuje jSerialComm z Desktopu)
# ==========================================================
# Opierając się na Twoim build.gradle, używasz biblioteki mik3y/usb-serial-for-android
-keep class com.hoho.android.usbserial.** { *; }
-dontwarn com.hoho.android.usbserial.**

# ==========================================================
# GOOGLE BILLING & ADMOB (Wymagane, by nie crashowało na Release)
# ==========================================================
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**

-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ==========================================================
# KOTLINX DATETIME & COROUTINES & IO
# ==========================================================
-keep class kotlinx.datetime.** { *; }
-dontwarn kotlinx.datetime.**
-dontnote kotlinx.datetime.**

-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-dontwarn kotlinx.io.**
-dontnote kotlinx.io.**

# ==========================================================
# NATYWNE METODY (JNI) I ZASOBY
# ==========================================================
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    static <fields>;
}

# Zasoby Compose Multiplatform
-keep class org.jetbrains.compose.resources.** { *; }
-dontwarn org.jetbrains.compose.resources.**

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn java.awt.Desktop$Action
-dontwarn java.awt.Desktop

# Zachowaj wszystkie enumy w projekcie
-keep enum pl.misek.toafmobile.** { *; }

# Ogólna reguła dla wszystkich enumów (zabezpieczenie na przyszłość)
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}