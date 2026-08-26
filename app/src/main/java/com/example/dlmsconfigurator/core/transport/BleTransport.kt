package com.example.dlmsconfigurator.core.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class BleTransport(
    private val context: Context,
    private val deviceAddress: String,
    private val customServiceUuid: String? = null,
    private val customTxUuid: String? = null,
    private val customRxUuid: String? = null
) : DlmsTransport {

    private val TAG = "BleTransport"

    // Default UUIDs: Nordic UART Service (NUS) & Standard Optical BLE
    private val NUS_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    private val NUS_TX_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e") // Write to device
    private val NUS_RX_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e") // Notify from device

    // Client Characteristic Configuration Descriptor (CCCD)
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    private val rxQueue = LinkedBlockingQueue<Byte>(32768)
    private var isConnected = false
    private var currentMtu = 23 // Default BLE MTU

    private val writeLock = ReentrantLock()
    private var writeLatch: CountDownLatch? = null

    @SuppressLint("MissingPermission")
    override fun open() {
        if (isConnected) return

        val adapter = bluetoothAdapter ?: throw IOException("Bluetooth adapter not available on device")
        if (!adapter.isEnabled) {
            throw IOException("Bluetooth is disabled. Please enable Bluetooth in phone settings.")
        }

        if (!BluetoothAdapter.checkBluetoothAddress(deviceAddress)) {
            throw IOException("Invalid Bluetooth MAC address: $deviceAddress")
        }

        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            throw IOException("Could not find Bluetooth device for address $deviceAddress: ${e.message}", e)
        }

        rxQueue.clear()

        val connectLatch = CountDownLatch(1)
        val serviceLatch = CountDownLatch(1)
        val notifyLatch = CountDownLatch(1)
        var connectionError: String? = null

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "onConnectionStateChange status=$status, newState=$newState")
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnected = true
                    connectLatch.countDown()
                    // Request high MTU for DLMS payload performance
                    try {
                        gatt.requestMtu(512)
                    } catch (e: Exception) {
                        Log.w(TAG, "Request MTU failed, proceeding with default MTU: ${e.message}")
                        gatt.discoverServices()
                    }
                } else {
                    isConnected = false
                    connectionError = "GATT connection failed with status code $status"
                    connectLatch.countDown()
                    serviceLatch.countDown()
                    notifyLatch.countDown()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "onMtuChanged mtu=$mtu, status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    currentMtu = mtu
                }
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Log.d(TAG, "onServicesDiscovered status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    serviceLatch.countDown()
                } else {
                    connectionError = "Service discovery failed with status $status"
                    serviceLatch.countDown()
                }
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                Log.d(TAG, "onDescriptorWrite status=$status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    notifyLatch.countDown()
                } else {
                    connectionError = "Failed to enable notifications on BLE optical characteristic (status $status)"
                    notifyLatch.countDown()
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                writeLock.withLock {
                    writeLatch?.countDown()
                }
            }

            @Deprecated("Used for Android < 13")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val value = characteristic.value ?: return
                for (b in value) {
                    rxQueue.offer(b)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                for (b in value) {
                    rxQueue.offer(b)
                }
            }
        }

        // Connect GATT
        Log.d(TAG, "Connecting to GATT device at $deviceAddress...")
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }

        if (!connectLatch.await(12, TimeUnit.SECONDS) || !isConnected) {
            close()
            throw IOException(connectionError ?: "Connection timeout connecting to BLE device $deviceAddress")
        }

        if (!serviceLatch.await(10, TimeUnit.SECONDS)) {
            close()
            throw IOException(connectionError ?: "Timeout discovering BLE GATT services")
        }

        // Locate RX and TX characteristics
        val gatt = bluetoothGatt ?: throw IOException("GATT instance is null")
        val serviceUuid = customServiceUuid?.let { UUID.fromString(it) } ?: NUS_SERVICE_UUID
        val txUuid = customTxUuid?.let { UUID.fromString(it) } ?: NUS_TX_UUID
        val rxUuid = customRxUuid?.let { UUID.fromString(it) } ?: NUS_RX_UUID

        var targetService = gatt.getService(serviceUuid)
        
        // If specified service not found, search all discovered services for RX/TX characteristics
        if (targetService == null) {
            for (service in gatt.services) {
                if (service.getCharacteristic(txUuid) != null || service.getCharacteristic(rxUuid) != null) {
                    targetService = service
                    break
                }
            }
        }

        if (targetService != null) {
            txCharacteristic = targetService.getCharacteristic(txUuid) ?: targetService.characteristics.firstOrNull { 
                (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || 
                (it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 
            }
            rxCharacteristic = targetService.getCharacteristic(rxUuid) ?: targetService.characteristics.firstOrNull { 
                (it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 || 
                (it.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 
            }
        } else {
            // Search globally across all services for writable and notify characteristics
            for (s in gatt.services) {
                for (c in s.characteristics) {
                    if (txCharacteristic == null && ((c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 || (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0)) {
                        txCharacteristic = c
                    }
                    if (rxCharacteristic == null && ((c.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 || (c.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0)) {
                        rxCharacteristic = c
                    }
                }
            }
        }

        val rxChar = rxCharacteristic ?: run {
            close()
            throw IOException("No BLE notification characteristic found on device $deviceAddress")
        }
        val txChar = txCharacteristic ?: run {
            close()
            throw IOException("No BLE writable characteristic found on device $deviceAddress")
        }

        // Enable Notifications
        gatt.setCharacteristicNotification(rxChar, true)
        val descriptor = rxChar.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            val descriptorValue = if ((rxChar.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, descriptorValue)
            } else {
                descriptor.value = descriptorValue
                gatt.writeDescriptor(descriptor)
            }

            if (!notifyLatch.await(5, TimeUnit.SECONDS)) {
                Log.w(TAG, "Notification descriptor write timed out, continuing connection attempt...")
            }
        }

        Log.i(TAG, "BLE Transport connected successfully to $deviceAddress (MTU: $currentMtu)")
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        isConnected = false
        try {
            bluetoothGatt?.disconnect()
        } catch (ignored: Exception) {}
        try {
            bluetoothGatt?.close()
        } catch (ignored: Exception) {}
        bluetoothGatt = null
        txCharacteristic = null
        rxCharacteristic = null
        rxQueue.clear()
    }

    override fun isOpen(): Boolean {
        return isConnected && bluetoothGatt != null
    }

    @SuppressLint("MissingPermission")
    override fun write(data: ByteArray) {
        if (!isOpen()) throw IOException("BLE connection not open")
        val gatt = bluetoothGatt ?: throw IOException("BLE GATT not connected")
        val txChar = txCharacteristic ?: throw IOException("BLE TX characteristic not found")

        // Chunk data based on effective MTU (MTU - 3 bytes GATT header)
        val maxChunkSize = (currentMtu - 3).coerceAtLeast(20)
        var offset = 0

        while (offset < data.size) {
            val chunkSize = (data.size - offset).coerceAtMost(maxChunkSize)
            val chunk = data.copyOfRange(offset, offset + chunkSize)

            writeLock.withLock {
                writeLatch = CountDownLatch(1)
                
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val writeType = if ((txChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    gatt.writeCharacteristic(txChar, chunk, writeType) == BluetoothGatt.GATT_SUCCESS
                } else {
                    txChar.value = chunk
                    if ((txChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        txChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        txChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    gatt.writeCharacteristic(txChar)
                }

                if (!success) {
                    throw IOException("Failed to send BLE packet chunk at offset $offset")
                }

                // If write with response, wait for onCharacteristicWrite
                if ((txChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) == 0) {
                    if (!writeLatch!!.await(3, TimeUnit.SECONDS)) {
                        Log.w(TAG, "BLE characteristic write acknowledgement timed out")
                    }
                }
            }

            offset += chunkSize
        }
    }

    override fun read(buffer: ByteArray, timeoutMs: Int): Int {
        if (!isOpen() && rxQueue.isEmpty()) throw IOException("BLE connection not open")

        val firstByte = rxQueue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            ?: return 0 // Timed out waiting for data

        buffer[0] = firstByte
        var bytesRead = 1

        // Drain available bytes without blocking
        while (bytesRead < buffer.size) {
            val nextByte = rxQueue.poll() ?: break
            buffer[bytesRead++] = nextByte
        }

        return bytesRead
    }

    override fun flush() {
        rxQueue.clear()
    }
}
