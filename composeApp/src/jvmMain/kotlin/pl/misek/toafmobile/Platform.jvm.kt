package pl.misek.toafmobile

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()
actual fun getPlatformType() = PlatformType.Desktop
actual fun getPlatformName() : String {
    val os = System.getProperty("os.name").lowercase()

    return when {
        os.contains("win") -> "WINDOWS"
        os.contains("linux") -> "LINUX"
        os.contains("nix") -> "UNIX"
        os.contains("mac") -> "MACOS"
        else -> "UNKNOWN"
    }
}