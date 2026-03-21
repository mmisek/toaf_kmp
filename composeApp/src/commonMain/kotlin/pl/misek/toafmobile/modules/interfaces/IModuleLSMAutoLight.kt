package pl.misek.toafmobile.modules.interfaces

interface IModuleLSMAutoLight : IModuleAdvanceCoding {
    fun detectAutoLightMode(coding: List<ByteArray>?): Int
    fun setAutoLightMode(coding: List<ByteArray>, mode: Int): List<ByteArray>
}
