package com.example.dlmsconfigurator.core.transport

import android.app.PendingIntent
import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
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

    private fun getDrivers(): List<UsbSerialDriver> {
        val defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (defaultDrivers.isNotEmpty()) {
            return defaultDrivers
        }

        val rawDevices = usbManager.deviceList.values
        if (rawDevices.isEmpty()) return emptyList()

        val customTable = ProbeTable()
        for (device in rawDevices) {
            customTable.addProduct(device.vendorId, device.productId, FtdiSerialDriver::class.java)
            customTable.addProduct(device.vendorId, device.productId, ProlificSerialDriver::class.java)
            customTable.addProduct(device.vendorId, device.productId, Cp21xxSerialDriver::class.java)
            customTable.addProduct(device.vendorId, device.productId, Ch34xSerialDriver::class.java)
            customTable.addProduct(device.vendorId, device.productId, CdcAcmSerialDriver::class.java)
        }
        val customProber = UsbSerialProber(customTable)
        return customProber.findAllDrivers(usbManager)
    }

    fun getDevice(): UsbDevice? {
        val drivers = getDrivers()
        if (drivers.isNotEmpty()) {
            return drivers.first().device
        }
        return usbManager.deviceList.values.firstOrNull()
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

        val drivers = getDrivers()
        val driver = if (drivers.isNotEmpty()) {
            drivers.first()
        } else {
            val rawDevice = usbManager.deviceList.values.firstOrNull() 
                ?: throw IOException("No USB optical probe detected. Make sure OTG is enabled in Phone Settings.")
            val customTable = ProbeTable()
            customTable.addProduct(rawDevice.vendorId, rawDevice.productId, FtdiSerialDriver::class.java)
            val customProber = UsbSerialProber(customTable)
            customProber.findAllDrivers(usbManager).firstOrNull() 
                ?: throw IOException("USB device (VID: ${rawDevice.vendorId}, PID: ${rawDevice.productId}) serial driver not found")
        }

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

    override fun flush() {
        val port = usbPort ?: return
        try {
            val buffer = ByteArray(1024)
            var read: Int
            do {
                read = port.read(buffer, 50)
            } while (read > 0)
        } catch (ignored: Exception) {}
    }
}
