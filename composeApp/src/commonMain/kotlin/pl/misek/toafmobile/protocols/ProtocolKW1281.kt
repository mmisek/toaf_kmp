package pl.misek.toafmobile.protocols

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.misek.toafmobile.interfaces.IInterfaceDevice
import pl.misek.toafmobile.interfaces.IKW1281InterfaceDevice

/**
 * Protokol typu KW1281 (Bosch ABS 5.x, program AVR "KWP91/92"):
 * - inicjalizacja 5 bodow adresem, potem 9600 8N1, sterownik wysyla 55 KB1 KB2, tester odsyla ~KB2,
 * - blok: [dlugosc][licznik][tytul][dane...][03], dlugosc = liczba bajtow po bajcie dlugosci,
 * - kazdy bajt poza koncowym 03 potwierdzany negacja przez strone odbierajaca,
 * - licznik zwiekszany przy kazdym bloku, tytul 09 - ACK (nic do wyslania).
 *
 * Sterownik konczy sesje, gdy tester przestanie wysylac bloki (program AVR wymienia bloki bez przerwy,
 * co ok. 50 ms) - po kazdej operacji sesja jest podtrzymywana w tle blokami ACK przez [KEEP_ALIVE_WINDOW_MS].
 * Echo linii K (adaptery KKL) wykrywane jest przy inicjalizacji i pomijane.
 */
class ProtocolKW1281(private var device: IInterfaceDevice?) : IProtocol {
    private val tag = "ProtocolKW1281"
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: Job? = null

    private var connectedAddress = -1
    private var blockCounter = 0
    private var pushedBack = -1
    private var echoMode = true

    /** Ostatnia wymiana na linii (udana lub nie) - do odstepu przed ponowna inicjalizacja */
    private var lastBusActivity = 0L
    /** Ostatnia udana wymiana bloku */
    private var lastActivity = 0L
    /** Ostatnia operacja zlecona przez modul - podtrzymanie sesji tylko przez okreslony czas po niej */
    private var lastRequest = 0L

    /** Bloki wysylane po kazdej inicjalizacji danego adresu (np. wgranie procedury do RAM sterownika) */
    private val sessionSetup = mutableMapOf<Int, List<Pair<Int, ByteArray>>>()

    /** Bloki tekstowe (tytul F6) wyslane przez sterownik po inicjalizacji */
    var identification: List<String> = emptyList()
        private set

    /** Zwiekszany przy kazdej udanej inicjalizacji - pozwala modulom wykryc nowa sesje */
    var sessionNumber = 0
        private set

    data class Block(val counter: Int, val title: Int, val data: ByteArray)

    override fun setDevice(newDevice: IInterfaceDevice?) {
        device = newDevice
        connectedAddress = -1
    }

    override fun getDevice(): IInterfaceDevice? = device

    override fun setSpeed(baud: Int) {}

    /** Bloki [tytul, dane] wysylane zaraz po inicjalizacji adresu, przed pierwsza operacja */
    fun setSessionSetup(address: Int, blocks: List<Pair<Int, ByteArray>>) {
        sessionSetup[address] = blocks
    }

    override suspend fun write(address: Byte, command: Byte, data: ByteArray): OBDMessage =
        write(address, command, data, null)

    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Long?): OBDMessage =
        write(address, command, data, timeout, true)

    /** Pojedyncza wymiana: blok [command, data], odpowiedz w OBDMessage.answer jako [tytul, dane...] */
    override suspend fun write(address: Byte, command: Byte, data: ByteArray, timeout: Long?, forceAnswer: Boolean): OBDMessage {
        val message = OBDMessage()
        val response = transact(address.toInt() and 0xFF, command.toInt() and 0xFF, data, false) ?: return message
        response.firstOrNull()?.let {
            message.answer = byteArrayOf(it.title.toByte()) + it.data
            message.answerCode = it.title.toByte()
            message.length = message.answer.size
            message.isValid = true
        }
        return message
    }

    /**
     * Wysyla blok i odbiera odpowiedz. Przy [multiBlock] potwierdza kolejne bloki ACK (09),
     * az sterownik odpowie ACK - zwracane sa bloki przed tym ACK. Po bledzie ponawia raz z nowa inicjalizacja.
     */
    suspend fun transact(address: Int, title: Int, data: ByteArray, multiBlock: Boolean): List<Block>? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                lastRequest = System.currentTimeMillis()
                repeat(2) { attempt ->
                    if (ensureSession(address, attempt > 0)) {
                        val result = exchange(title, data, multiBlock)
                        if (result != null) {
                            startKeepAlive()
                            return@withContext result
                        }
                        Logger.e { "$tag : exchange failed, title %02X".format(title) }
                        connectedAddress = -1
                    }
                }
                null
            }
        }

    private fun kw(): IKW1281InterfaceDevice? = device as? IKW1281InterfaceDevice

    private fun ensureSession(address: Int, forceInit: Boolean): Boolean {
        if (!forceInit && connectedAddress == address) {
            if (System.currentTimeMillis() - lastActivity < SESSION_IDLE_MS) return true
            // dluzsza przerwa - sprawdzenie sesji blokiem ACK, przy braku odpowiedzi nowa inicjalizacja
            if (exchange(ACK, ByteArray(0), false) != null) return true
            connectedAddress = -1
        }
        repeat(INIT_ATTEMPTS) {
            if (initialize(address)) return true
        }
        return false
    }

    /** Podtrzymanie sesji blokami ACK przez [KEEP_ALIVE_WINDOW_MS] od ostatniej operacji modulu */
    private fun startKeepAlive() {
        if (keepAliveJob?.isActive == true) return
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(KEEP_ALIVE_INTERVAL_MS)
                if (connectedAddress < 0 || System.currentTimeMillis() - lastRequest > KEEP_ALIVE_WINDOW_MS) break
                mutex.withLock {
                    if (connectedAddress >= 0 && System.currentTimeMillis() - lastActivity >= KEEP_ALIVE_INTERVAL_MS) {
                        if (exchange(ACK, ByteArray(0), false) == null) {
                            Logger.e { "$tag : keep alive failed" }
                            connectedAddress = -1
                        }
                    }
                }
            }
        }
    }

    private fun initialize(address: Int): Boolean {
        val kw = kw() ?: return false
        connectedAddress = -1
        pushedBack = -1
        // sterownik musi zakonczyc poprzednia (zerwana) sesje, zanim przyjmie nowa inicjalizacje
        val idle = System.currentTimeMillis() - lastBusActivity
        if (idle < REINIT_IDLE_MS) sleep(REINIT_IDLE_MS - idle)
        Logger.i { "$tag : 5 baud init 0x%02X".format(address) }
        val result = initializeSession(kw, address)
        lastBusActivity = System.currentTimeMillis()
        if (!result) return false
        identification = initIdentification
        sessionNumber++
        connectedAddress = address
        lastActivity = lastBusActivity
        // bloki przygotowania sesji (np. wgranie procedury) - blad przerywa inicjalizacje
        for ((title, data) in sessionSetup[address].orEmpty()) {
            if (exchange(title, data, true) == null) {
                Logger.e { "$tag : session setup failed, title %02X".format(title) }
                connectedAddress = -1
                return false
            }
        }
        return true
    }

    private var initIdentification: List<String> = emptyList()

    private fun initializeSession(kw: IKW1281InterfaceDevice, address: Int): Boolean {
        if (!kw.kw1281SlowInit(address, BAUDRATE)) {
            Logger.e { "$tag : slow init - port error" }
            return false
        }
        // synchronizacja 55, potem bajty klucza KB1 KB2
        val start = System.currentTimeMillis()
        var sync = -1
        while (System.currentTimeMillis() - start < SYNC_TIMEOUT_MS) {
            sync = kw.kw1281ReadByte(SYNC_TIMEOUT_MS)
            if (sync == 0x55 || sync == -1) break
            Logger.i { "$tag : skipped byte before sync %02X".format(sync) }
        }
        if (sync != 0x55) {
            Logger.e { "$tag : no sync byte" }
            return false
        }
        val keyByte1 = kw.kw1281ReadByte(KEY_BYTE_TIMEOUT_MS)
        val keyByte2 = kw.kw1281ReadByte(KEY_BYTE_TIMEOUT_MS)
        if (keyByte1 < 0 || keyByte2 < 0) {
            Logger.e { "$tag : no key bytes ($keyByte1, $keyByte2)" }
            return false
        }
        Logger.i { "$tag : key bytes %02X %02X".format(keyByte1, keyByte2) }
        sleep(KEY_BYTE_ANSWER_DELAY_MS)
        // wykrycie echa: adapter KKL zwraca wyslany bajt, adapter bez echa - od razu dane sterownika
        val answer = keyByte2 xor 0xFF
        if (!kw.kw1281WriteByte(answer)) return false
        val echo = kw.kw1281ReadByte(ECHO_TIMEOUT_MS)
        echoMode = echo == answer
        if (echo >= 0 && echo != answer) pushedBack = echo
        Logger.i { "$tag : echo ${if (echoMode) "on" else "off"}" }
        // bloki identyfikacji (F6) az do ACK sterownika
        val ident = mutableListOf<String>()
        while (true) {
            val block = receiveBlock() ?: return false
            if (block.title == ACK) break
            if (block.title == TITLE_ASCII) ident.add(String(CharArray(block.data.size) { (block.data[it].toInt() and 0xFF).toChar() }).trim())
            if (!sendBlock(ACK, ByteArray(0))) return false
        }
        initIdentification = ident
        return true
    }

    private fun ByteArray.toHex() = joinToString(" ") { "%02X".format(it) }

    private fun exchange(title: Int, data: ByteArray, multiBlock: Boolean): List<Block>? {
        try {
            if (!sendBlock(title, data)) return null
            val result = mutableListOf<Block>()
            while (true) {
                val block = receiveBlock() ?: return null
                lastActivity = System.currentTimeMillis()
                if (!multiBlock) return listOf(block)
                if (block.title == ACK) return result
                result.add(block)
                if (!sendBlock(ACK, ByteArray(0))) return null
                if (result.size > MAX_BLOCKS) return result
            }
        } finally {
            lastBusActivity = System.currentTimeMillis()
        }
    }

    private fun sendBlock(title: Int, data: ByteArray): Boolean {
        val block = byteArrayOf((data.size + 3).toByte(), blockCounter.toByte(), title.toByte()) + data
        Logger.i { "$tag TX : ${block.toHex()} 03" }
        blockCounter = (blockCounter + 1) and 0xFF
        // przerwa po bloku sterownika jak w programie AVR (50 ms) - wczesniejsze nadawanie koliduje z ECU
        sleep(BLOCK_GAP_MS)
        for ((index, value) in block.withIndex()) {
            if (index > 0) sleep(INTER_BYTE_DELAY_MS)
            val b = value.toInt() and 0xFF
            if (!writeWithEcho(b)) return false
            val complement = readByte(BYTE_TIMEOUT_MS)
            if (complement != (b xor 0xFF)) {
                Logger.e { "$tag : bad complement %02X for %02X".format(complement, b) }
                return false
            }
        }
        sleep(INTER_BYTE_DELAY_MS)
        return writeWithEcho(BLOCK_END)
    }

    private fun receiveBlock(): Block? {
        val length = readByte(BLOCK_TIMEOUT_MS)
        if (length < 3 || length > MAX_BLOCK_LENGTH) {
            Logger.e { "$tag : no block ($length)" }
            return null
        }
        val bytes = IntArray(length)
        if (!complement(length)) return null
        for (i in 0 until length) {
            val b = readByte(BYTE_TIMEOUT_MS)
            if (b < 0) {
                Logger.e { "$tag : block byte $i of $length missing" }
                return null
            }
            bytes[i] = b
            if (i == length - 1) break
            if (!complement(b)) return null
        }
        if (bytes[length - 1] != BLOCK_END) {
            Logger.e { "$tag : bad block end %02X".format(bytes[length - 1]) }
            return null
        }
        blockCounter = (bytes[0] + 1) and 0xFF
        Logger.i { "$tag RX : %02X ".format(length) + bytes.joinToString(" ") { "%02X".format(it) } }
        return Block(bytes[0], bytes[1], ByteArray(length - 3) { bytes[it + 2].toByte() })
    }

    private fun complement(b: Int): Boolean {
        sleep(INTER_BYTE_DELAY_MS)
        return writeWithEcho(b xor 0xFF)
    }

    /**
     * Zapis bajtu; przy adapterze z echem czeka na echo (opoznienie USB, np. FTDI na PC do 16 ms).
     * Sterownik nadaje dopiero po odebraniu naszego bajtu, wiec echo jest pierwsze na linii -
     * kazdy inny bajt przed nim to zaklocenie i jest odrzucany (cofniecie go rozsynchronizowaloby wymiane).
     */
    private fun writeWithEcho(b: Int): Boolean {
        val kw = kw() ?: return false
        if (!kw.kw1281WriteByte(b)) return false
        if (!echoMode) return true
        repeat(MAX_NOISE_BYTES + 1) {
            val echo = kw.kw1281ReadByte(ECHO_TIMEOUT_MS)
            if (echo == b) return true
            if (echo < 0) {
                Logger.e { "$tag : no echo for %02X".format(b) }
                return false
            }
            Logger.e { "$tag : noise %02X before echo %02X".format(echo, b) }
        }
        return false
    }

    private fun readByte(timeoutMs: Long): Int {
        if (pushedBack >= 0) return pushedBack.also { pushedBack = -1 }
        return kw()?.kw1281ReadByte(timeoutMs) ?: -1
    }

    private fun sleep(ms: Long) {
        if (ms > 0) Thread.sleep(ms)
    }

    companion object {
        const val ACK = 0x09
        const val TITLE_ASCII = 0xF6
        private const val BLOCK_END = 0x03
        private const val BAUDRATE = 9600
        private const val SYNC_TIMEOUT_MS = 1000L
        private const val KEY_BYTE_TIMEOUT_MS = 200L
        private const val BYTE_TIMEOUT_MS = 200L
        private const val ECHO_TIMEOUT_MS = 100L
        private const val BLOCK_TIMEOUT_MS = 1000L
        private const val KEY_BYTE_ANSWER_DELAY_MS = 25L
        private const val INTER_BYTE_DELAY_MS = 2L
        private const val BLOCK_GAP_MS = 50L
        private const val MAX_NOISE_BYTES = 3
        private const val MAX_BLOCK_LENGTH = 0x7F
        private const val SESSION_IDLE_MS = 400L
        private const val KEEP_ALIVE_INTERVAL_MS = 250L
        private const val KEEP_ALIVE_WINDOW_MS = 30_000L
        private const val REINIT_IDLE_MS = 2000L
        private const val INIT_ATTEMPTS = 2
        private const val MAX_BLOCKS = 32
    }
}
