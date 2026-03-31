# ==========================================================
# GŁÓWNE REGUŁY APLIKACJI I COMPOSE
# ==========================================================
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Zachowaj główną klasę startową aplikacji (Zmień na swoją paczkę!)
-keep class pl.misek.toafmobile.MainKt {
    public static void main(java.lang.String[]);
}

# ==========================================================
# KOIN (Dependency Injection)
# ==========================================================
# Koin używa refleksji do wyszukiwania definicji i modułów.
-keep class org.koin.** { *; }
-keepnames class * extends org.koin.core.module.Module

# Jeśli wstrzykujesz ViewModel (choć na desktopie rzadziej używane w standardowy sposób)
-keep class * extends androidx.lifecycle.ViewModel { *; }


# ==========================================================
# KTOR (Networking)
# ==========================================================
# Ktor ładuje silniki (np. CIO, OkHttp) używając java.util.ServiceLoader.
# Musimy upewnić się, że klasy silników i konfiguracji nie zostaną usunięte.
-keep class io.ktor.** { *; }
-keep class * implements io.ktor.client.engine.HttpClientEngine { *; }
-keep class * implements io.ktor.client.engine.HttpClientEngineFactory { *; }

# Zachowaj obsługę ServiceLoader dla Coroutines i Ktor
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}


# ==========================================================
# KOTLINX SERIALIZATION (Zazwyczaj używane z Ktorem)
# ==========================================================
# Jeśli używasz @Serializable, klasa nie może zostać zaciemniona,
# ponieważ Ktor nie będzie w stanie zmapować JSONa.
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.** { *; }


# ==========================================================
# ROOM (Baza danych KMP) & SQLITE
# ==========================================================
# Room na desktopie korzysta pod spodem z SQLite JDBC.
# Zachowanie adnotacji oraz klas wygenerowanych przez KSP.
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# BARDZO WAŻNE DLA DESKTOPU: 
# Sterownik SQLite (xerial sqlite-jdbc) używa JNI do ładowania natywnych bibliotek 
# (.dll dla Windows, .so dla Linuxa). Nie wolno go usunąć ani zaciemnić.
-keep class org.sqlite.** { *; }
-keep class org.sqlite.jdbc3.** { *; }
-keep class org.sqlite.jdbc4.** { *; }


# ==========================================================
# KERMIT (Logging)
# ==========================================================
# Kermit jest prosty, ale w przypadku używania tagów opartych na nazwach klas, 
# refleksja może spowodować błędy, jeśli zaciemnimy klasy loggera.
-keep class co.touchlab.kermit.** { *; }


# ==========================================================
# POPRAWKI DLA WSTRZYKIWANIA ZALEŻNOŚCI (KOIN)
# ==========================================================
# Zachowaj struktury klas, które wstrzykujesz do ViewModeli
-keep class pl.misek.toafmobile.network.** { *; }
-keep class pl.misek.toafmobile.database.** { *; }
-keep class pl.misek.toafmobile.modules.interfaces.** { *; }
-keep class pl.misek.toafmobile.coding.** { *; }
-keep class pl.misek.toafmobile.control.** { *; }
-keep class pl.misek.toafmobile.protocols.** { *; }
-keep class pl.misek.toafmobile.interfaces.** { *; }

# Zmuś ProGuarda, aby nie ruszał konstruktorów w Twoich ViewModelach.
# Dzięki temu Koin będzie widział, jakich typów (Api, Database) potrzebuje.
-keepclassmembers class pl.misek.toafmobile.viewModels.** {
    public <init>(...);
}

# ==========================================================
# IGNOROWANIE BRAKUJĄCYCH REFERENCJI (UNRESOLVED REFERENCES)
# ==========================================================
# Wyłączenie ostrzeżeń dla bibliotek, które bezpiecznie możemy zignorować,
# ponieważ ich brakujące klasy to zazwyczaj opcjonalne zależności na JVM.
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class kotlinx.io.Source { *; }
-keep class kotlinx.io.Sink { *; }
-keep class kotlinx.io.Buffer { *; }
-keep class kotlinx.io.RawSource { *; }
-keep class kotlinx.io.RawSink { *; }
-keep class kotlinx.io.Segment { *; }
-keep class kotlinx.io.bytestring.ByteString { *; }
-dontwarn kotlinx.io.**
-dontnote kotlinx.io.**

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
-dontwarn org.koin.**
-dontwarn androidx.room.**
-dontwarn org.sqlite.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**
-dontwarn co.touchlab.kermit.**
-dontwarn com.sun.jna.**

# Opcjonalnie: Jeśli używasz biblioteki DateTime od JetBrains
-keep class kotlinx.datetime.YearMonth { *; }
-keep class kotlinx.datetime.Month { *; }
-keep class kotlinx.datetime.TimeZone { *; }
-keep class kotlinx.datetime.LocalDateTime { *; }
-keep class kotlinx.datetime.LocalDate { *; }
-keep class kotlinx.datetime.OverloadMarker { *; }

-dontwarn kotlinx.datetime.**
-dontnote kotlinx.datetime.**
# ==========================================================
# COMPOSE MULTIPLATFORM RESOURCES
# ==========================================================
# Chroni klasy zasobów (np. StringResource, DrawableResource)
-keep class org.jetbrains.compose.resources.** { *; }
-dontwarn org.jetbrains.compose.resources.**

# ==========================================================
# JSERIALCOMM (Komunikacja sprzętowa / USB)
# ==========================================================
# BEZWZGLĘDNIE zachowujemy całą bibliotekę sprzętową ze względu na JNI
# i wyłączamy dla niej ostrzeżenia (co usunie pozostałe 58 warningów)
-keep class com.fazecast.jSerialComm.** { *; }
-dontwarn com.fazecast.jSerialComm.**

# ==========================================================
# Room - database
# ==========================================================
-keep class androidx.sqlite.** { *;}
-dontwarn androidx.sqlite.*
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers class * {
    static <fields>;
}