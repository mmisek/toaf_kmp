package pl.misek.toafmobile.modules.containers

data class BCUKey (
    val id :  Int,
    var key: String
)
{
    init
    {
        key = key.uppercase()
    }

    fun updateKey(newKey: String)
    {
        key = newKey.uppercase()
    }
}
