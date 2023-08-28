package com.testproject;

import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.usb.UsbConnection;

public class Printer {
    public int id;
    public String type;
    public String name;
    public String target;
    public UsbConnection usbConnection;
    public BluetoothConnection bluetoothConnection;

    public Printer(int id, String type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
    }
    public Printer(int id, String type, String name, UsbConnection usbConnection) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.usbConnection = usbConnection;
    }
    public Printer(int id, String type, String name, BluetoothConnection bluetoothConnection) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.bluetoothConnection = bluetoothConnection;
    }

    public Printer(int id, String type, String name, String target) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.target = target;
    }
}
