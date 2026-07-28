package com.example.dlmsconfigurator.core.transport

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbSerialTransport(
    context: Context,
    private val baudRate: Int
) : DlmsTransport {
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var usbPort: UsbSerialPort? = null
    private var usbConnection: UsbDeviceConnection? = null

    fun getDevice(): UsbDevice? {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        return availableDrivers.firstOrNull()?.device
    }

    fun hasPermission(): Boolean {
        val device = getDevice() ?: return false
        return usbManager.hasPermission(device)
    }

    fun requestPermission(intent: PendingIntent) {
        val device = getDevice() ?: throw IOException("No USB device found")
        usbManager.requestPermission(device, intent)
    }

    override fun open() {
        if (usbPort != null) return

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            throw IOException("No USB serial devices found")
        }

        val driver = availableDrivers.first()
        val device = driver.device
        
        if (!usbManager.hasPermission(device)) {
            throw SecurityException("USB permission denied")
        }

        val connection = usbManager.openDevice(device) ?: throw IOException("Could not open USB connection")
        val port = driver.ports[0]
        
        try {
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            this.usbPort = port
            this.usbConnection = connection
        } catch (e: Exception) {
            try { port.close() } catch (ignored: Exception) {}
            try { connection.close() } catch (ignored: Exception) {}
            throw e
        }
    }

    override fun close() {
        try {
            usbPort?.close()
        } catch (ignored: Exception) {}
        usbPort = null
        try {
            usbConnection?.close()
        } catch (ignored: Exception) {}
        usbConnection = null
    }

    override fun isOpen(): Boolean {
        return usbPort?.isOpen == true
    }

    override fun write(data: ByteArray) {
        val port = usbPort ?: throw IOException("Port not open")
        port.write(data, 5000)
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        val port = usbPort ?: throw IOException("Port not open")
        return port.read(buffer, timeoutMs)
    }
}
