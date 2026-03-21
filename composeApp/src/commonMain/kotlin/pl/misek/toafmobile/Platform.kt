package pl.misek.toafmobile

interface Platform {
    val name: String
}
enum class PlatformType
{
    Android,
    Desktop
}
expect fun getPlatform(): Platform
expect fun getPlatformType() : PlatformType