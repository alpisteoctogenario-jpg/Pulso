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
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val chars = g.services.flatMap { it.characteristics }
            tx = chars.firstOrNull { it.uuid in txIds }
            last = -1
            if (tx != null) onStatus("Conectado ✔", true)
            else onStatus("Conectado, pero no reconozco el canal: " + chars.joinToString { it.uuid.toString().take(8) }, false)
        }
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
