package com.example.proctoring_app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class USBDeviceManager(private val context: Context) {

    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val usbDeviceList: HashMap<String, UsbDevice> by lazy {
        usbManager.deviceList
    }

    fun getConnectedUSBDevices(): List<UsbDevice> {
        val connectedDevices = mutableListOf<UsbDevice>()

        for (device in usbDeviceList.values) {
            if (usbManager.hasPermission(device)) {
                connectedDevices.add(device)
            }
        }

        return connectedDevices
    }

    private val usbDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    if (usbManager.hasPermission(device)) {
                        // Handle the attached USB device
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    // Handle the detached USB device
                }
            }
        }
    }
    fun registerUSBDeviceReceiver() {
        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        context.registerReceiver(usbDeviceReceiver, filter)
    }
    fun unregisterUSBDeviceReceiver() {
        context.unregisterReceiver(usbDeviceReceiver)
    }
}