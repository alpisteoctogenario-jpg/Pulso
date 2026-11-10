package app.pulso

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/** Conexión BLE con Lovense. Envía comandos de texto "Vibrate:N;" (N = 0..20). */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class Lovense(private val ctx: Context, private val onStatus: (String, Boolean) -> Unit) {
    private val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var gatt: BluetoothGatt? = null
    @Volatile private var tx: BluetoothGattCharacteristic? = null
    private var scanning = false
    @Volatile private var last = -1
    private var lastAt = 0L
    private val main = Handler(Looper.getMainLooper())
    var onRx: (String) -> Unit = {}

    // Canales TX conocidos de distintas generaciones de Lovense
    private val txIds = listOf(
        "50300002-0023-4bd4-bbd5-a6920e4c5653",
        "5a300002-0023-4bd4-bbd5-a6920e4c5653",
        "5a300002-0024-4bd4-bbd5-a6920e4c5653",
        "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
        "0000fff2-0000-1000-8000-00805f9b34fb"
    ).map { UUID.fromString(it) }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            val n = r.device.name ?: r.scanRecord?.deviceName ?: return
            if (n.startsWith("LVS-", true) || n.contains("lovense", true)) { stopScan(); open(r.device) }
        }
        override fun onScanFailed(e: Int) { scanning = false; onStatus("Error de escaneo ($e)", false) }
    }

    fun connect() {
        if (adapter?.isEnabled != true) { onStatus("Activa el Bluetooth", false); return }
        close()
        scanning = true
        onStatus("Buscando… enciende el dispositivo", false)
        runCatching { adapter.bluetoothLeScanner.startScan(scanCb) }
            .onFailure { scanning = false; onStatus("Faltan permisos de Bluetooth", false) }
        main.postDelayed({ if (scanning) { stopScan(); onStatus("No encontrado", false) } }, 15000)
    }

    private fun stopScan() { if (scanning) { scanning = false; runCatching { adapter.bluetoothLeScanner.stopScan(scanCb) } } }
    private fun open(d: BluetoothDevice) {
        onStatus("Conectando…", false)
        gatt = d.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE)
    }
    fun close() { stopScan(); tx = null; gatt?.close(); gatt = null }

    private val cb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, state: Int) {
            if (state == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else { tx = null; g.close(); if (gatt === g) gatt = null; onStatus("Desconectado", false) }
        }
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            val v = c.value ?: return
            val asc = v.joinToString("") { b -> val i = b.toInt() and 0xFF; if (i in 32..126) i.toChar().toString() else "." }
            val hex = v.joinToString(" ") { b -> "%02x".format(b.toInt() and 0xFF) }
            onRx("$asc | $hex")
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val chars = g.services.flatMap { it.characteristics }
            val re = Regex("^[0-9a-f]{2}300002-00(23|24)-4bd4-bbd5-a6920e4c5653$")
            tx = chars.firstOrNull { it.uuid in txIds || re.matches(it.uuid.toString().lowercase()) }
            last = -1
            // Notificaciones del dispositivo (batería, movimiento, etc.) para el "laboratorio"
            val rre = Regex("^[0-9a-f]{2}300003-00(23|24)-4bd4-bbd5-a6920e4c5653$")
            val rxc = chars.firstOrNull { val u = it.uuid.toString().lowercase(); rre.matches(u) || u.startsWith("6e400003") || u.startsWith("0000fff1") }
            if (rxc != null) {
                g.setCharacteristicNotification(rxc, true)
                rxc.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let { d -> d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
            }
            if (tx != null) onStatus("Conectado ✔", true)
            else onStatus("Conectado, pero no reconozco el canal: " + chars.joinToString { it.uuid.toString() }, false)
        }
    }

    /** Comando de consulta (lista blanca en Engine). */
    fun sendRaw(cmd: String) {
        val c = tx ?: return; val g = gatt ?: return
        c.value = cmd.toByteArray(); if (g.writeCharacteristic(c)) last = -1
    }

    /** level 0..20. Solo reenvía si cambia o cada 1 s (keep-alive). */
    fun send(level: Int, force: Boolean = false) {
        val c = tx ?: return
        val g = gatt ?: return
        val now = System.currentTimeMillis()
        if (!force && level == last && now - lastAt < 1000) return
        c.value = "Vibrate:${level.coerceIn(0, 20)};".toByteArray()
        if (g.writeCharacteristic(c)) { last = level; lastAt = now }
    }
}
