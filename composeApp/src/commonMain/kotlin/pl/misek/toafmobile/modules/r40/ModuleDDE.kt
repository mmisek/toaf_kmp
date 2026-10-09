package pl.misek.toafmobile.modules.r40

import co.touchlab.kermit.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.StringResource
import pl.misek.toafmobile.coding.ZcsCoding
import pl.misek.toafmobile.control.ControlElement
import pl.misek.toafmobile.generated.ResourceHelper
import pl.misek.toafmobile.generated.ResourceMapper
import pl.misek.toafmobile.koin.KoinNames
import pl.misek.toafmobile.livedata.LiveDataElement
import pl.misek.toafmobile.modules.DDEErrorsMatrix
import pl.misek.toafmobile.modules.DDEFreezeFrame
import pl.misek.toafmobile.modules.ModuleError
import pl.misek.toafmobile.modules.ModuleInfo
import pl.misek.toafmobile.modules.enums.CarType
import pl.misek.toafmobile.modules.enums.FlashProgrammingState
import pl.misek.toafmobile.modules.enums.ModuleAddress
import pl.misek.toafmobile.activities.composeModels.activities.AdvanceControlEmsEwsSync
import pl.misek.toafmobile.modules.interfaces.AdvanceControlElement
import pl.misek.toafmobile.modules.interfaces.EwsSyncStep
import pl.misek.toafmobile.modules.interfaces.IModule
import pl.misek.toafmobile.modules.interfaces.IModuleAdvanceControl
import pl.misek.toafmobile.modules.interfaces.IModuleCoding
import pl.misek.toafmobile.modules.interfaces.IModuleControl
import pl.misek.toafmobile.modules.interfaces.IModuleEwsSync
import pl.misek.toafmobile.modules.interfaces.IModuleFlashProgramming
import pl.misek.toafmobile.modules.interfaces.IModuleLiveData
import pl.misek.toafmobile.modules.interfaces.IModuleZCS
import pl.misek.toafmobile.protocols.IProtocol
import pl.misek.toafmobile.protocols.ProtocolDDE
import pl.misek.toafmobile.tools.JsonSupport
import toafmobile.composeapp.generated.resources.Res
import toafmobile.composeapp.generated.resources.*
import java.util.Arrays
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ThreadLocalRandom

class ModuleDDE(private var kwp: ProtocolDDE) : IModuleZCS, IModuleFlashProgramming, IModule,
    IModuleControl,
    IModuleCoding, IModuleLiveData, IModuleEwsSync, IModuleAdvanceControl
{
    private val tag = "ModuleDDE"
    private val moduleAddress = ModuleAddress.MODULE_DDE.address
    private val liveDataBlocksToRead: MutableList<Int> =ArrayList()
    private val ddeCodingCells = byteArrayOf(0xA1.toByte(),0xA3.toByte(),0xA8.toByte())

    private var localDdeErrorsMatrix: Array<DDEErrorsMatrix> = arrayOf(
        DDEErrorsMatrix(0x0100,byteArrayOf(0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),
                                                    0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                                                    0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x03.toByte(),
                                                    0x00.toByte(),0x02.toByte(),0x00.toByte(),0x42.toByte(),0x00.toByte(),0x01.toByte(),
                                                    0x00.toByte(),0x3F.toByte(),0x00.toByte(),0x3C.toByte(),0x00.toByte(),0x00.toByte(),
                                                    0x00.toByte(),0x20.toByte())),
        DDEErrorsMatrix(0x0105,byteArrayOf(0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),
                                                    0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                                                    0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x3F.toByte(),
                                                    0x00.toByte(),0x3C.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                                                    0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                                                    0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0110,byteArrayOf(0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                                                    0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),
                                                    0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x3D.toByte(),
                                                    0x00.toByte(),0x3E.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                                                    0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0115,byteArrayOf(0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),
                                                    0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),
                                                    0x00.toByte(),0x3D.toByte(),0x00.toByte(),0x3E.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                                                    0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())        ),
        DDEErrorsMatrix(0x0120,byteArrayOf(0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),
                                                    0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                                                    0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x3C.toByte(),0x00.toByte(),0x42.toByte(),
                                                    0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                                                    0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0190,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),
                0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x1C.toByte(),
                0x00.toByte(),0x47.toByte(),0x00.toByte(),0x42.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0200,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),
                0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),
                0x00.toByte(),0x44.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x43.toByte(),0x00.toByte(),0x1D.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x39.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0201,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),
                0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),
                0x44.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x43.toByte(),0x00.toByte(),0x1D.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x39.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0202,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),
                0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x44.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x43.toByte(),0x00.toByte(),0x1D.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x39.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0203,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),
                0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),
                0x44.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x43.toByte(),0x00.toByte(),0x1D.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x39.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0204,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),
                0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x44.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x43.toByte(),0x00.toByte(),0x1D.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x39.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0205,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),
                0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),
                0x44.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x43.toByte(),0x00.toByte(),0x1D.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x39.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0206,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),
                0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x44.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x43.toByte(),0x00.toByte(),0x1D.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x39.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0220,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),
                0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x1B.toByte(),0x00.toByte(),0x42.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x30.toByte())),
        DDEErrorsMatrix(0x0235,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),
                0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),
                0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x3F.toByte(),0x00.toByte(),0x3C.toByte(),0x00.toByte(),
                0x42.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x2D.toByte())),
        DDEErrorsMatrix(0x0335,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),
                0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),
                0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x27.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x3A.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0400,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),
                0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x05.toByte(),0x00.toByte(),0x06.toByte(),0x00.toByte(),0x32.toByte(),0x00.toByte(),
                0x29.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0404,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),
                0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x29.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0480,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),
                0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x52.toByte(),0x00.toByte(),0x53.toByte(),0x00.toByte(),
                0x05.toByte(),0x00.toByte(),0x06.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x54.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0500,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),
                0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x15.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x3B.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x50.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x2E.toByte())),
        DDEErrorsMatrix(0x0560,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),
                0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x49.toByte(),0x00.toByte(),0x2B.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte())),
        DDEErrorsMatrix(0x0600,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),
                0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x0A.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte())),
        DDEErrorsMatrix(0x0605,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),
                0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x34.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x55.toByte(),0x00.toByte(),0x35.toByte(),0x00.toByte(),0x13.toByte(),0x00.toByte(),0x25.toByte(),
                0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x09F6,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),
                0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x5E.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1190,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),
                0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x22.toByte(),0x00.toByte(),0x26.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x33.toByte(),0x00.toByte(),0x1E.toByte(),0x00.toByte(),0x36.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x5D.toByte())),
        DDEErrorsMatrix(0x1195,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x48.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x0F.toByte())),
        DDEErrorsMatrix(0x1250,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),
                0x48.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1255,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),
                0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x1C.toByte(),0x00.toByte(),0x47.toByte(),0x00.toByte(),0x42.toByte(),0x00.toByte(),0x00.toByte(),   0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1260,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x24.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x4D.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())        ),
        DDEErrorsMatrix(0x1470,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),
                0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x28.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())        ),
        DDEErrorsMatrix(0x1612,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x58.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1613,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),
                0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x51.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1640,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x04.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x19.toByte(),0x00.toByte(),0x17.toByte(),0x00.toByte(),0x23.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x19E6,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x48.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte())),
        DDEErrorsMatrix(0x1A04,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x59.toByte(),0x00.toByte(),0x5A.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x5B.toByte(),
                0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x48.toByte())),
        DDEErrorsMatrix(0x1A0E,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x14.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x14.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1A18,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),
                0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x12.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1A22,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x48.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1DF5,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x21.toByte(),0x00.toByte(),0x46.toByte(),0x00.toByte(),0x45.toByte(),0x00.toByte(),0x4A.toByte(),0x00.toByte(),
                0x4E.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E00,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x0C.toByte(),0x00.toByte(),0x0B.toByte(),0x00.toByte(),0x0E.toByte(),0x00.toByte(),0x0D.toByte(),0x00.toByte(),0x56.toByte(),
                0x00.toByte(),0x57.toByte(),0x00.toByte(),0x09.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E05,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x18.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E25,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x2A.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x07.toByte(),
                0x00.toByte(),0x1A.toByte(),0x00.toByte(),0x08.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E30,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),   0xF4.toByte(),0xF5.toByte(),
                0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x05.toByte(),0x00.toByte(),0x06.toByte(),0x00.toByte(),0x31.toByte(),0x00.toByte(),0x28.toByte(),0x00.toByte(),
                0x5C.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E35,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x11.toByte(),0x00.toByte(),0x10.toByte())),
        DDEErrorsMatrix(0x1E40,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),
                0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x48.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x1F.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E45,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),
                0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x40.toByte(),0x00.toByte(),0x41.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E50,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x40.toByte(),0x00.toByte(),
                0x41.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E55,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x48.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x1E60,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),
                0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x48.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x2800,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),
                0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x4C.toByte(),0x00.toByte(),0x4B.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x3000,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x2C.toByte())),
        DDEErrorsMatrix(0x3005,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x4F.toByte())),
        DDEErrorsMatrix(0x3010,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),
                0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x2F.toByte())),
        DDEErrorsMatrix(0x3505,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x05.toByte(),0x00.toByte(),0x06.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x16.toByte())),
        DDEErrorsMatrix(0x3510,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),
                0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x37.toByte(),0x00.toByte(),0x38.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x3515,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),
                0xF3.toByte(),0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x1B.toByte(),0x00.toByte(),0x48.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x3520,byteArrayOf(
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0xEB.toByte(),0xEC.toByte(),0x00.toByte(),0xEE.toByte(),0x00.toByte(),0x00.toByte(),0xF1.toByte(),0xF2.toByte(),0xF3.toByte(),
                0xF4.toByte(),0xF5.toByte(),0xF6.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x3C.toByte(),0x00.toByte(),0x3F.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),
                0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte(),0x00.toByte())),
        DDEErrorsMatrix(0x0404, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x29.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())),
        DDEErrorsMatrix(0x1612, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x58.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())),
        DDEErrorsMatrix(0x1613, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x51.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())),
        DDEErrorsMatrix(0x19E6, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1B.toByte(), 0x00.toByte(), 0x48.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())),
        DDEErrorsMatrix(0x1A04, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x59.toByte(), 0x00.toByte(), 0x5A.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x5B.toByte(), 0x00.toByte(), 0x1B.toByte(), 0x00.toByte(), 0x48.toByte())),
        DDEErrorsMatrix(0x1A0E, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x14.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x14.toByte(), 0x00.toByte(), 0x00.toByte())),
        DDEErrorsMatrix(0x1A18, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x12.toByte(), 0x00.toByte(), 0x00.toByte())),
        DDEErrorsMatrix(0x1A22, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1B.toByte(), 0x00.toByte(), 0x48.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())),
        DDEErrorsMatrix(0x1DF5, byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xEB.toByte(), 0xEC.toByte(), 0x00.toByte(), 0xEE.toByte(), 0x00.toByte(), 0x00.toByte(), 0xF1.toByte(), 0xF2.toByte(), 0xF3.toByte(),
            0xF4.toByte(), 0xF5.toByte(), 0xF6.toByte(), 0x00.toByte(), 0x21.toByte(), 0x00.toByte(), 0x46.toByte(), 0x00.toByte(), 0x45.toByte(), 0x00.toByte(), 0x4A.toByte(), 0x00.toByte(), 0x4E.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())))
    private val _programmingState =  MutableSharedFlow<FlashProgrammingState>(replay = 10, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val programmingState = _programmingState.asSharedFlow()

    override fun getKoinName() = KoinNames.ModuleR40Dde

    override fun getModuleProtocol(): IProtocol {
        return kwp
    }

    override fun setModuleProtocol(protocol: IProtocol) {
        kwp = protocol as ProtocolDDE
    }

    override suspend fun readZCS(): ZcsCoding? {
        if (securityAccess()) {
            val message =  kwp.write(moduleAddress, 0x22.toByte(), byteArrayOf(0x40, 0x09), 500)
            if (message.isValid) {
                try {
                    val data = message.answer.copyOfRange(7, message.length - 1)
                    return ZcsCoding(data)
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex : Exception)
                {
                    Logger.e{"$tag [readZCS()], $ex, $message"}
                    return null
                }
            }
        }
        return null
    }

    override suspend fun writeZCS(data: ZcsCoding): Boolean {
        val d = byteArrayOf(0x40, 0x09) + data.bytes
        if (securityAccess()) {
            for (i in 0..2) {
                val message = kwp.write(moduleAddress, 0x2E.toByte(), d, 3000)
                if (message.isValid) return true
            }
        }
        return false
    }

    override suspend fun reset(): Boolean {
        return kwp.write(moduleAddress, 0x12.toByte(), byteArrayOf(0x82.toByte())).isValid
    }

    override suspend fun startLiveDataRead(liveDataElements: List<LiveDataElement>): Boolean {
        liveDataBlocksToRead.clear()
        for (el in liveDataElements) liveDataBlocksToRead.add(el.block)
        getIdentification()
        return true
    }

    override suspend fun readLiveData(): Map<Int, ByteArray>? {
        val result: MutableMap<Int, ByteArray> = HashMap()
        val mess = ByteArray(1 + liveDataBlocksToRead.size * 2)
        mess[0] = 0x10
        for (i in liveDataBlocksToRead.indices) {
            val address = liveDataBlocksToRead[i]
            mess[2 * i + 1] = ((address and 0xFF00) shr 8).toByte()
            mess[2 + 2 * i] = (address and 0xFF).toByte()
        }
        val message = kwp.write(moduleAddress, 0x2C.toByte(), mess)
        if (message.isValid) {
            try {
                for (i in liveDataBlocksToRead.indices) result[liveDataBlocksToRead[i]] =
                    byteArrayOf(message.answer[6 + 2 * i], message.answer[7 + 2 * i])
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e {"$tag [readLiveData()], $ex, $message"}
                return null
            }
        }
        return result
    }

    override suspend fun control(controlElement: ControlElement): Boolean {
        return if (securityAccess()) {
            if (controlElement.controlSetValue != null) kwp.write(
                moduleAddress,
                0x30.toByte(),
                byteArrayOf(
                    controlElement.block.toByte(),
                    0x07,
                    controlElement.controlSetValue!!.value.toByte()
                )
            ).isValid
            else kwp.write(
                moduleAddress,
                0x30.toByte(),
                byteArrayOf(
                    controlElement.block.toByte(),
                    0x07,
                    controlElement.value.toByte()
                )
            ).isValid
        } else false
    }

    override fun getImageResource() = Res.drawable.module_engine

    override fun getModuleDescription() = Res.string.module_description_dde

    override fun getModuleName() = "DDE"

    override fun getModuleAddress(): ModuleAddress {
        return ModuleAddress.MODULE_DDE
    }

    override suspend fun clearAllErrors(): Boolean {
        return kwp.write(moduleAddress, 0x14.toByte(), byteArrayOf(0x00, 0x00)).isValid
    }

    override suspend fun readCoding(): List<ByteArray>? {
        val result: MutableList<ByteArray> = ArrayList()

        for (ddeCodingCell in ddeCodingCells) {
            val message = kwp.write(moduleAddress, 0x30.toByte(), byteArrayOf(ddeCodingCell, 0x01))
            if (message.isValid) {
                try {
                    result.add(message.answer.copyOfRange(7, message.answer.size - 1))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex : Exception)
                {
                    Logger.e {"$tag [readCoding()], $ex, $message"}
                    return null
                }
            } else return null
        }
        return result
    }

    override suspend fun writeCoding(dataToSave: List<ByteArray>): Boolean {
        val codingElements = JsonSupport.getCodingList(getModuleAddress(), 0)
        for (codingElement in codingElements) {
            for ( i in 0..3)
            {
                val message = kwp.write(
                    moduleAddress, 0x30.toByte(),
                    byteArrayOf(
                        ddeCodingCells[codingElement.block],
                        0x07
                    ) + dataToSave[codingElement.block]
                )
                if (message.isValid)
                    break
                delay(300)
                if (i == 3 ) return false
            }
            for (i in 0..3)
            {
                val message =  kwp.write(
                    moduleAddress,
                    0x30.toByte(),
                    byteArrayOf(ddeCodingCells[codingElement.block], 0x08)
                )
                if (message.isValid)
                    break
                delay(300)
                if (i == 3) return false
            }
        }
        reset()
        return true
    }

    override suspend fun getAllErrors(): List<ModuleError>? {
        var result: MutableList<ModuleError>? = null
        val message = kwp.write(moduleAddress, 0x18.toByte(), byteArrayOf(0x00, 0x00, 0x00))
        if (message.isValid) {
            try {
                val freezeFramesList = JsonSupport.getFreezeFrameList()
                for (i in 0 until (message.answer[5].toInt() and 0xFF)) {
                    val errorCode =
                        ((message.answer[6 + 3 * i].toInt() and 0xFF) shl 8) + (message.answer[7 + 3 * i].toInt() and 0xFF)
                    val mess = kwp.write(
                        moduleAddress, 0x17.toByte(), byteArrayOf(
                            message.answer[6 + 3 * i],
                            message.answer[7 + 3 * i]
                        )
                    )
                    val errorDescription: MutableList<Pair<StringResource, Any?>> = ArrayList()
                    if (mess.isValid) {
                        errorDescription.add(ResourceHelper.getErrorDescriptionCount((mess.answer[8].toInt() and 0xFF)))
                        for (k in 0..7) {
                            val number = mess.answer[10].toInt() and (0x01 shl k)
                            if (number == (0x01 shl k)) {
                                ResourceMapper.stringResource(
                                    String.format(
                                        "dde_error_detail_%02X",
                                        number
                                    )
                                )
                                    ?.let { errorDescription.add(Pair(it, null)) }
                            }
                        }
                        var freezeFrame: DDEFreezeFrame? = null
                        try {
                            freezeFrame = freezeFramesList?.let {
                                JsonSupport.getFreezeFrameForErrorCode(
                                    it,
                                    String.format("%04X", errorCode)
                                )
                            }
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (_: Exception) {
                            Logger.e {
                                "DDE freeFrame error code " + String.format(
                                    "%04X",
                                    errorCode
                                )
                            }
                        }
                        if (freezeFrame != null) {
                            var n = mess.answer[9].toInt() and 0xFF
                            if (n > 3) n = 3
                            for (j in 0 until n) {
                                errorDescription.add(
                                    Pair(
                                        Res.string.dde_error_freeze_frame_number,
                                        (j + 1).toString()
                                    )
                                )
                                val errorsMatrix = Arrays.stream(localDdeErrorsMatrix)
                                    .filter { x: DDEErrorsMatrix -> x.errorCode == errorCode }
                                    .findFirst().get()
                                for (k in 0..7) {
                                    for (index in 10..11) {
                                        val number = mess.answer[index].toInt() and (0x01 shl k)
                                        if (number == (0x01 shl k) && errorsMatrix.errorMatrix[2 * k + 1].toInt() != 0x00) {
                                            ResourceMapper.stringResource(
                                                String.format(
                                                    "dde_error_detail_%02X",
                                                    number
                                                )
                                            )
                                                ?.let { errorDescription.add(Pair(it, null)) }
                                        }
                                    }
                                }

                                for (k in freezeFrame.ffDetails.indices) {
                                    val freezeFrameDetail = freezeFrame.ffDetails[k]

                                    if ((mess.answer[11 + 7 * j].toInt() and freezeFrameDetail.first) == freezeFrameDetail.first) {
                                        errorDescription.add(Pair(freezeFrameDetail.second!!, null))
                                    }
                                }

                                for (k in freezeFrame.ffItems.indices) {
                                    val ff = freezeFrame.ffItems[k]
                                    val x = mess.answer[12 + 7 * j + k].toInt() and 0xFF
                                    ResourceMapper.stringResource("dde_error_ff_" + ff.name)
                                        ?.let {
                                            errorDescription.add(
                                                Pair(
                                                    it,
                                                    String.format(
                                                        Locale.getDefault(),
                                                        "%.2f %s",
                                                        (x * ff.factorA + ff.factorB).toDouble(),
                                                        ff.unit
                                                    )
                                                )
                                            )
                                        }
                                }
                                val x =
                                    ((mess.answer[16 + 7 * j].toInt() and 0xFF) shl 8) + (mess.answer[17 + 7 * j].toInt() and 0xFF)
                                errorDescription.add(
                                    Pair(
                                        Res.string.dde_error_ff_odometer,
                                        String.format(Locale.getDefault(), "%d km", x)
                                    )
                                )
                            }
                        } else
                            Logger.e {
                                "$tag : No freeze frame for code ${
                                    String.format(
                                        "%04X",
                                        errorCode
                                    )
                                }"
                            }
                        if (result == null) result = ArrayList()
                        result.add(
                            ModuleError(
                                ModuleAddress.MODULE_DDE,
                                String.format("%04X", errorCode).uppercase(
                                    Locale.getDefault()
                                ),
                                errorDescription
                            )
                        )
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag, [getAllErros()], $ex, $message"}
            }
        }
        return result
    }

    override suspend fun getIdentification(): ModuleInfo? {
        var info: ModuleInfo? = null
        var message = kwp.write(moduleAddress, 0xA2.toByte(), ByteArray(0))
        if (message.isValid) {
            try {
                val answer = message.answer
                info = ModuleInfo()
                info.id2 = String(answer.copyOfRange(24, 33))
                info.hw = String(answer.copyOfRange(12, 14))
                info.ci = String(answer.copyOfRange(14, 16))
                info.di = String(answer.copyOfRange(16, 18))
                info.bi = String(answer.copyOfRange(18, 20))
                info.sw = String(answer.copyOfRange(34, 36))
                info.date = String(answer.copyOfRange(20, 22)) + "/" + String(answer.copyOfRange(22,24))
                info.serial = String(answer.copyOfRange(38, 46))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e {"$tag, [getIdentification()], $ex, $message"}
            }
        }
        message = kwp.write(
            moduleAddress,
            0x23.toByte(),
            byteArrayOf(0x0F, 0xF9.toByte(), 0x00, 0x06, 0x2E)
        )
        if (message.isValid) {
            try {
                if (info == null) info = ModuleInfo()
                info.id1 = String(
                        message.answer.copyOfRange(25,28)
                ) + String.format(
                    "%02X%02X%02X",
                    message.answer[28].toInt() and 0xFF,
                    message.answer[29].toInt() and 0xFF,
                    message.answer[30].toInt() and 0xFF
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e {"$tag, [getIdentification()], $ex, $message"}
            }
        }
        return info
    }
    /*
    fun setVIN(newVIN: String?): Boolean {
        val message = kwp.write(
                    moduleAddress,
                    0x23.toByte(),
                    byteArrayOf(0x87.toByte(), 0xF9.toByte(), 0x00, 0x06, 0x2E))
                if (message.isValid) {
                    val data = message.answer.copyOfRange(5, message.answer.size - 1)
                    kwp.write(moduleAddress, 0xA2.toByte(), byteArrayOf())
                    kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x01))
                    if (securityAccess()) {
                        kwp.write(moduleAddress, 0x30.toByte(), byteArrayOf(0xEB.toByte(), 0x01))
                        if (startDiagnosticSession()) {
                            kwp.setSpeed(38400)
                            val m1 = kwp.write(
                                moduleAddress,
                                0x30.toByte(),
                                byteArrayOf(0xEB.toByte(), 0x01)
                            )
                            val m2 = kwp.write(
                                moduleAddress,
                                0x31.toByte(),
                                byteArrayOf(
                                    0x02,
                                    0x08,
                                    0xF9.toByte(),
                                    0x2E,
                                    0x06,
                                    0x00,
                                    0x00,
                                    0x00
                                ), 2000
                            )
                            val m3 = kwp.write(
                                moduleAddress,
                                0x34.toByte(),
                                byteArrayOf(
                                    0x08.toByte(),
                                    0xF9.toByte(),
                                    0x2E,
                                    0x06,
                                    0x00,
                                    0x00,
                                    0x2E.toByte(),
                                    0x00
                                ), 500
                            )
                            if (m1.isValid && m2.isValid && m3.isValid) {
                                writeFlashData(data)
                            }
                            if (kwp.write(
                                    moduleAddress,
                                    0x37.toByte(),
                                    byteArrayOf()
                                ).isValid
                            ) kwp.write(
                                moduleAddress,
                                0x30.toByte(),
                                byteArrayOf(0xEB.toByte(), 0x01)
                            )
                            kwp.write(
                                moduleAddress,
                                0x30.toByte(),
                                byteArrayOf(0xEB.toByte(), 0x01)
                            )
                            kwp.setSpeed(9600)
                            reset()
                        }
                    }
                }
        return true
    }
    */

    override suspend fun getVIN(): String? {
        val message = kwp.write(
            moduleAddress,
            0x23.toByte(),
            byteArrayOf(0x0F, 0xF9.toByte(), 0x00, 0x06, 0x2E)
        )
        if (message.isValid) {
            try {
                return String(
                    message.answer.copyOfRange(5, 16)
                ) + String.format(
                    Locale.getDefault(),
                    "%02d%02d%02d",
                    message.answer[16].toInt() and 0xFF,
                    message.answer[17].toInt() and 0xFF,
                    message.answer[18].toInt() and 0xFF
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getVIN()], $ex, $message"}
                return null
            }
        }
        return null
    }

    override suspend fun getOdometer(): String? {
        val message =
            kwp.write(moduleAddress, 0x2C.toByte(), byteArrayOf(0x10, 0x0F, 0xD0.toByte()))
        if (message.isValid) {
            try {
                return String.format(Locale.getDefault(), "%d", ((message.answer[6].toInt() and 0xFF) shl 8) + (message.answer[7].toInt() and 0xFF))
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex : Exception)
            {
                Logger.e {"$tag [getOdometer()], $ex, $message"}
                return null
            }
        }
        return null
    }

    suspend fun liveDataInjectorsEnable(): Boolean {
        return kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x40, 0x00)).isValid
    }

    /**
     * Synchronizacja EWS - DDE4 (wg logu rzeczywistej sesji).
     * Kolejnosc: 1) IDENT DDE, 2) IDENT EWS (DS2 0x00 -> A0), 3) handshake EWS (DS2 0x67 -> A0),
     * 4) wejscie w tryb sync (KWP 0x31 {0x83,0x01} -> 0x71), 5) odczyt statusu (KWP 0x21 {0x06} -> 0x61).
     * Wymaga podlaczonego modulu EWS.
     */
    override suspend fun syncEws(ews: ModuleEWS?, onStep: (EwsSyncStep) -> Unit): Boolean {
        if (ews == null) return false
        val ecuIdent = getIdentification()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ecu_ident, !ecuIdent?.id1.isNullOrEmpty(), ecuIdent?.id1))
        if (ecuIdent?.id1.isNullOrEmpty()) return false
        val ewsId = ews.getIdentification()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ews_ident, !ewsId?.id1.isNullOrEmpty(), ewsId?.id1))
        if (ewsId?.id1.isNullOrEmpty()) return false
        val handshake = ews.triggerDmeSync()
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_ews_handshake, handshake))
        if (!handshake) return false
        val enter = kwp.write(moduleAddress, 0x31.toByte(), byteArrayOf(0x83.toByte(), 0x01)).isValid
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_enter, enter))
        if (!enter) return false
        val statusMsg = kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x06))
        val status = statusMsg.isValid
        val statusDetail = try {
            if (status && statusMsg.answer.size > 6) "0x%02X".format(statusMsg.answer[6]) else null
        } catch (ex: Exception) { null }
        onStep(EwsSyncStep(Res.string.advance_control_ews_sync_step_status, status, statusDetail))
        return status
    }

    override fun getAdvanceControlElements() = listOf(
        AdvanceControlElement(Res.string.advance_control_ews_sync_title) { userInfo, onShowResult ->
            AdvanceControlEmsEwsSync(userInfo = userInfo, onShowResult = onShowResult)
        }
    )

    private suspend fun securityAccess(): Boolean {
        var result = false
        val level = ThreadLocalRandom.current().nextInt(3, 20)
        var message = kwp.write(
            moduleAddress,
            0x27.toByte(),
            byteArrayOf(0x02, 0x42, 0x4D, 0x57, (level * 2).toByte()),
            500
        )
        if (message.isValid) {
                message = kwp.write(moduleAddress, 0x27.toByte(), byteArrayOf(0x01), 800)
                if (message.isValid) {
                    try {
                        if (message.answerCode == 0x67.toByte() && message.length == 9 && message.answer[6] == 0x00.toByte() && message.answer[7] == 0x00.toByte())
                            return true
                        val seed = ByteArray(4)
                        for (i in 0..3) {
                            val a = message.answer[level * 2 + i].toInt()
                            val b = message.answer[0x2C + i].toInt()
                            val c = message.answer[0x15 + i].toInt()
                            val x = (a + b + c) and 0xFF
                            seed[i] = x.toByte()
                            seed[i] =
                                ((message.answer[level * 2 + i] + message.answer[0x2C + i] + message.answer[0x15 + i]) and 0xFF).toByte()
                        }
                        message = kwp.write(
                            moduleAddress,
                            0x27.toByte(),
                            byteArrayOf(0x04) + seed,
                            500
                        )
                        result = message.isValid
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Logger.e {"$tag, [securityAccess], $ex, $message"}
                        result = false
                    }
                }
            }
        return result
    }

    override suspend fun writeFlash(data: ByteArray) {
        kwp.setSpeed(9600)
        val blockSize = 0x80
        val steps = data.size / blockSize
        val startTime = Calendar.getInstance().time

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                Res.string.activity_flash_programming_file_size,
                data.size.toString()))
        if (data.size != 48 * 1024) {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_wrong_file_size))
        } else {
            //_programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_ecu_reset))
            //reset()
            //try {
            //    delay(10 * 1000)
            //} catch (ex: CancellationException) {
            //    throw ex
            //} catch (_ : Exception) {
            //}
            val moduleInfo = getIdentification()
            val vin = getVIN()
            if (moduleInfo != null) {
                _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo.id1))
                _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo.id2))
                _programmingState.tryEmit(FlashProgrammingState.Message("Serial : " + moduleInfo.serial))
            }
            _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))
            kwp.write(moduleAddress, 0xA2.toByte(), byteArrayOf())
            kwp.write(moduleAddress, 0x21.toByte(), byteArrayOf(0x01))
            if (securityAccess()) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_security_access,
                        Res.string.activity_flash_programming_ok))
                kwp.write(moduleAddress, 0x30.toByte(), byteArrayOf(0xEB.toByte(), 0x01))
                if (startDiagnosticSession()) {
                    try {
                        _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                Res.string.activity_flash_programming_diagnostic_session,
                                Res.string.activity_flash_programming_ok))
                        kwp.setSpeed(38400)
                            val m1 = kwp.write(
                                moduleAddress,
                                0x30.toByte(),
                                byteArrayOf(0xEB.toByte(), 0x01)
                            )
                            val m2 = kwp.write(
                                moduleAddress,
                                0x31.toByte(),
                                byteArrayOf(0x02, 0x0F, 0x00, 0x00, 0x06, 0x00, 0x00, 0x00), 2000
                            )
                            delay(500)
                            val m3 = kwp.write(
                                moduleAddress,
                                0x34.toByte(),
                                byteArrayOf(
                                    0x0F,
                                    0x00,
                                    0x00,
                                    0x06,
                                    0x00,
                                    0x00,
                                    0xC0.toByte(),
                                    0x00
                                ),
                                500
                            )
                            if (m1.isValid && m2.isValid && m3.isValid) {
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_erasing,
                                        Res.string.activity_flash_programming_ok))
                                delay(200)

                                var i = 0
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_write,
                                        String.format(Locale.getDefault(), "%2d %%", 0)))
                                var x = 0
                                while (x < data.size) {
                                    val dataToWrite = data.copyOfRange( x, x + blockSize)
                                    if (writeFlashData(dataToWrite))
                                        _programmingState.tryEmit(FlashProgrammingState.Progress(i++ * 100 / steps))
                                    else {
                                        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_write_error))
                                        break
                                    }
                                    x += blockSize
                                }
                                if (kwp.write(
                                        moduleAddress,
                                        0x37.toByte(),
                                        byteArrayOf()
                                    ).isValid
                                ) _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_writing_end,
                                        Res.string.activity_flash_programming_ok)
                                    )
                                else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_writing_end,
                                        Res.string.activity_flash_programming_failed))
                                kwp.write(
                                    moduleAddress,
                                    0x30.toByte(),
                                    byteArrayOf(0xEB.toByte(), 0x01)
                                )
                                kwp.setSpeed(9600)
                                val endTime = Calendar.getInstance().time
                                val totalMillis = endTime.time - startTime.time
                                val seconds = (totalMillis / 1000).toInt() % 60
                                val minutes = ((totalMillis / 1000).toInt() / 60) % 60
                                val hours = (totalMillis / 1000).toInt() / 3600
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_write_time,
                                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
                                    ))
                                reset()
                            } else {
                                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                                        Res.string.activity_flash_programming_flash_erasing,
                                        Res.string.activity_flash_programming_failed))
                            }
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (ex: Exception) {
                        Logger.e {"$tag [writeFlash] : $ex"}
                    }
                } else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_diagnostic_session,
                        Res.string.activity_flash_programming_failed))
            } else _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_security_access,
                    Res.string.activity_flash_programming_failed))
        }
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
    }

    override suspend fun readFlash() {
        val startAddress: Long = 0x870000
        val size: Long = 0xC000
        var result = ByteArray(0)
        val blockSize = 0xF0
        val endAddress = startAddress + size
        val steps = size.toInt() / blockSize
        val startTime = Calendar.getInstance().time
        kwp.setSpeed(9600)
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_start))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_read_identification))
        val moduleInfo = getIdentification()
        if (moduleInfo != null) {
            _programmingState.tryEmit(FlashProgrammingState.Message("ID1 : " + moduleInfo.id1))
            _programmingState.tryEmit(FlashProgrammingState.Message("ID2 : " + moduleInfo.id2))
            _programmingState.tryEmit(FlashProgrammingState.Message("Serial : " + moduleInfo.serial))
        }
        else {
            _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_failed))
            return
        }
        val vin = getVIN()
        vin?.let {
            _programmingState.tryEmit(FlashProgrammingState.Message("VIN : $vin"))
        }
        if (securityAccess()) {
            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_security_access,
                    Res.string.activity_flash_programming_ok))
            if (startDiagnosticSession()) {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_diagnostic_session,
                        Res.string.activity_flash_programming_ok))
                try {
                    kwp.setSpeed(38400)
                }
                catch (ex: CancellationException) {
                    throw ex
                }
                catch (ex: Exception)
                {
                    Logger.e {"$tag : Set speed to 38400 not supported, $ex"}
                    _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_failed))
                    return
                }
                delay(1000)

                var x = 0f
                _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_flash_reading))

                var i = startAddress
                while (i < endAddress) {
                    _programmingState.tryEmit(FlashProgrammingState.Progress(((x++ * 100) / steps).toInt()))
                    val data = readFlashData(i)
                    if (data.size == blockSize) {
                        result += data
                    } else {
                        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_read_error))
                        return
                    }
                    i += blockSize.toLong()
                }

                val endTime = Calendar.getInstance().time
                val totalMillis = endTime.time - startTime.time
                val seconds = (totalMillis / 1000).toInt() % 60
                val minutes = ((totalMillis / 1000).toInt() / 60) % 60
                val hours = (totalMillis / 1000).toInt() / 3600

                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_read_time,
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)))
            } else {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                        Res.string.activity_flash_programming_diagnostic_session,
                        Res.string.activity_flash_programming_failed))
            }
        } else {
            _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(
                    Res.string.activity_flash_programming_security_access,
                    Res.string.activity_flash_programming_failed))
        }

        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_close))
        _programmingState.tryEmit(FlashProgrammingState.Message(Res.string.activity_flash_programming_end))
        reset()
        kwp.setSpeed(9600)
        try {
            if (result.size >= (endAddress - startAddress).toInt()) {
                result = result.copyOfRange(0, (endAddress - startAddress).toInt())
                _programmingState.tryEmit(
                    FlashProgrammingState.FlashData(
                        result.toList(), moduleInfo, vin
                    )
                )
            }
            else
            {
                _programmingState.tryEmit(FlashProgrammingState.MessageWithParameter(Res.string.activity_flash_programming_read_flash, Res.string.activity_flash_programming_failed))
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Logger.e {"$tag [readFlash]: ${ex}, Result Size : ${result.size}"}
        }
    }

    private suspend fun readFlashData(address: Long): ByteArray {
        val size = 240
        val message = kwp.write(
            moduleAddress, 0x23.toByte(),
            byteArrayOf(
                ((address and 0xFF0000L) shr 16).toByte(),
                ((address and 0x00FF00L) shr 8).toByte(),
                ((address and 0x0000FFL)).toByte(),
                0x06,
                ((size and 0xFF)).toByte()
            ),
            1500
        )
        if (message.isValid) {
            try {
                return message.answer.copyOfRange(5, message.length - 1)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Logger.e { "$tag [readFlashData], $ex, $message" }
                return ByteArray(0)
            }
        }
        return ByteArray(0)
    }

    private suspend fun writeFlashData(data: ByteArray): Boolean {
        // Przy 38400 8E1 echo bloku 128 B + odpowiedz ECU to ~40 ms, a budzet czasu
        // w SerialDevice.write to 2 * timeout. 20 ms (budzet 40 ms) bywa za krotkie i
        // uciecie odpowiedzi daje falszywe isValid=false -> przerwanie zapisu w polowie.
        return kwp.write(
            moduleAddress,
            0x36.toByte(),
            data, 150
        ).isValid
    }

    override val flashProgrammingAvailable: Boolean = true

    private suspend fun startDiagnosticSession(): Boolean {
        return kwp.write(
            moduleAddress,
            0x10.toByte(),
            byteArrayOf(0x85.toByte(), 0x03),
            500
        ).isValid
    }

    override fun getSupportedCarTypes(): List<CarType> {
        return listOf(CarType.R40, CarType.L322)
    }
}
