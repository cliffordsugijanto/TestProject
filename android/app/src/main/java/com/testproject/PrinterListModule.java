package com.testproject;

import static com.testproject.ListPrinterRecyclerAdapter.BLUETOOTH_TYPE;
import static com.testproject.ListPrinterRecyclerAdapter.EPSON_TYPE;
import static com.testproject.ListPrinterRecyclerAdapter.USB_TYPE;
import static com.testproject.ListPrinterRecyclerAdapter.WIFI_TYPE;

import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.connection.tcp.TcpConnection;
import com.dantsu.escposprinter.connection.usb.UsbConnection;
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections;
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.dantsu.escposprinter.exceptions.EscPosEncodingException;
import com.dantsu.escposprinter.exceptions.EscPosParserException;
import com.epson.epos2.Epos2Exception;
import com.epson.epos2.discovery.DeviceInfo;
import com.epson.epos2.discovery.Discovery;
import com.epson.epos2.discovery.DiscoveryListener;
import com.epson.epos2.discovery.FilterOption;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import java.util.ArrayList;
import java.util.List;
/**
 * Native module to get list of available printers and print function
 */
public class PrinterListModule extends ReactContextBaseJavaModule {
    ReactApplicationContext mContext;

    //for epson case. if isDiscoverStarted==true, then we stop the discovery before start again
    boolean isDiscoverStarted;

    //list of printers, to get the reference from printer name
    List<Printer> printers = new ArrayList<>();

    public static final String BT_PRINTER = "BT Printer";
    public static final String USB_PRINTER = "USB Printer";
    public static final String WIFI_EPSON_PRINTER = "Wifi Epson Printer";
    public static final String EPSON_PRINTER = "Epson Printer";

    public PrinterListModule(ReactApplicationContext reactContext) {
        super(reactContext);
        mContext = reactContext;
    }

    @NonNull
    @Override
    public String getName() {
        return "PrinterListModule"; //use this name when import it in RN
    }

    /**
     * to get the list of available printers via bluetooth/usb. and for epson can be via usb/wifi
     * send the printer informations via sendEventToReactFromAndroid using event emitter
     */
    @ReactMethod
    public void getListOfPrinters() {
        printers.clear();
        /**
         * bluetooth printers section. might need to add permission request if its not granted yet.
         */
        BluetoothPrintersConnections btConnections = new BluetoothPrintersConnections();
        UsbPrintersConnections usbPrintersConnections = new UsbPrintersConnections(mContext);
        BluetoothConnection[] bluetoothConnections = btConnections.getList();
        if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(mContext, "Activate permission for nearby device / bluetooth", Toast.LENGTH_SHORT).show();
        }
        Log.e("refreshList", "refreshList4");
        if(bluetoothConnections!=null) {
            for (BluetoothConnection connection : bluetoothConnections) {
                Log.e("refreshList", "refreshList5");
                BluetoothDevice device = connection.getDevice();

                printers.add(new Printer(0, BLUETOOTH_TYPE, BT_PRINTER + " - "+device.getName() + " (" + device.getAddress() + ")", connection));
                WritableMap params = Arguments.createMap();
                params.putString("printerName",BT_PRINTER + " - "+ device.getName() + " (" + device.getAddress() + ")");
                sendEventToReactFromAndroid(mContext, "PrinterEvent",params);
            }
        }
        /**
         * usb printers section
         */
        UsbConnection[] usbConnections = usbPrintersConnections.getList();
        if(usbConnections!=null){
            for(UsbConnection connection : usbConnections){
                Log.e("refreshList", "refreshList6");
                UsbDevice device = connection.getDevice();
                printers.add(new Printer(1, USB_TYPE, USB_PRINTER + " - "+device.getProductName() + " (" + device.getManufacturerName() + ")", connection));
                WritableMap params = Arguments.createMap();
                params.putString("printerName",USB_PRINTER + " - "+ device.getProductName() + " (" + device.getManufacturerName() + ")");
                sendEventToReactFromAndroid(mContext, "PrinterEvent",params);
            }
        }

        /**
         * epson printers section. stop discovery if already started before
         */
        Log.e("refreshList", "refreshList7");
        FilterOption mFilterOption = new FilterOption();
        mFilterOption.setDeviceType(Discovery.TYPE_PRINTER);
        mFilterOption.setEpsonFilter(Discovery.FILTER_NAME);
        mFilterOption.setUsbDeviceName(Discovery.TRUE);

        if(isDiscoverStarted) {
            while (true) {
                try {
                    Log.e("refreshList", "refreshList8");
                    Discovery.stop();
                    isDiscoverStarted = false;
                    break;
                } catch (Epos2Exception e) {
                    if (e.getErrorStatus() != Epos2Exception.ERR_PROCESSING) {
                    }
                }
            }
        }

        try {
            Discovery.start(mContext, mFilterOption, new DiscoveryListener() {
                @UiThread
                @Override
                public void onDiscovery(DeviceInfo deviceInfo) {
                    if(!deviceInfo.getIpAddress().isEmpty()){
                        printers.add(new Printer(2, WIFI_TYPE, WIFI_EPSON_PRINTER + " - "+deviceInfo.getDeviceName()+" "+deviceInfo.getIpAddress(), deviceInfo.getTarget()));
                        WritableMap params = Arguments.createMap();
                        params.putString("printerName",WIFI_EPSON_PRINTER + " - "+ deviceInfo.getDeviceName()+" "+deviceInfo.getIpAddress());
                        sendEventToReactFromAndroid(mContext, "PrinterEvent",params);
                    }
                    else {
                        printers.add(new Printer(3, EPSON_TYPE, EPSON_PRINTER + " - "+deviceInfo.getDeviceName() + " " + deviceInfo.getIpAddress(), deviceInfo.getTarget()));
                        WritableMap params = Arguments.createMap();
                        params.putString("printerName", EPSON_PRINTER + " - "+ deviceInfo.getDeviceName() + " " + deviceInfo.getIpAddress());
                        sendEventToReactFromAndroid(mContext, "PrinterEvent",params);
                    }
                }
            });
            isDiscoverStarted = true;
            Log.e("refreshList", "refreshList9");
        }
        catch (Epos2Exception e) {
            e.printStackTrace();
            Log.e("refreshList", "refreshList10 errorstatus:"+e.getErrorStatus());
            try {
                Discovery.stop();
                isDiscoverStarted = false;
                Log.e("refreshList", "refreshList11");
                getListOfPrinters();
            } catch (Epos2Exception e1) {
                if (e1.getErrorStatus() != Epos2Exception.ERR_PROCESSING) {
                    return;
                }
            }
        }

    }

    /**
     * we use this to communicate with RN, to send the printer informations. then in RN code this will be processed on the eventEmitter listener
     * @param reactContext
     * @param eventName : we fill it with "PrinterEvent", make sure it match with the event name in the RN code
     * @param params : we send the printer name as "printerName" in the params. so we can access it in RN later as event.printerName
     */
    private void sendEventToReactFromAndroid(ReactApplicationContext reactContext, String eventName, @Nullable WritableMap params) {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class).emit(eventName, params);
    }

    /**
     * print functions. we call this on RN side for each printer control
     * @param printerName : the printer name we got from getListOfPrinters
     * @param text : the texts that will be printed
     */
    @ReactMethod
    public void print(String printerName, String text) {
        for(Printer p : printers){
            //we match it with the printers list, then get its details for print purposes
            if(p.name.equals(printerName)){
                if(p.type.equals(USB_TYPE)){
                    printUsb(p.usbConnection, text);
                }
                else if(p.type.equals(BLUETOOTH_TYPE)){
                    printBluetooth(p.bluetoothConnection, text);
                }
                else if(p.type.equals(EPSON_TYPE)){
                    if(p.target!=null) {
                        //initialize the printer first
                        initializeEpsonPrinter(p.target);
                    }
                    //then print the texts
                    runPrintReceiptSequence(text);
                }
                else if(p.type.equals(WIFI_TYPE)){
                    printWifi(p.name, text);
                }
            }
        }
    }

    /**
     * print USB section
     */
    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    /**
     * print using usb printer. but we process the print function in usbReceiver
     * @param usbConnection : needed for print purpose
     * @param text : text to print
     */
    public void printUsb(UsbConnection usbConnection, String text) {
        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        if (usbConnection != null && usbManager != null) {
            Intent intent = new Intent(ACTION_USB_PERMISSION);
            intent.putExtra("textToPrint", text);
            if(text==null||text.isEmpty())
                return;
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    mContext,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0)
            );
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            mContext.getApplicationContext().registerReceiver(this.usbReceiver, filter);
            usbManager.requestPermission(usbConnection.getDevice(), permissionIntent);
        }
    }

    /**
     * to process the usb printing
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String textToPrint = intent.getStringExtra("textToPrint");
            /*
                IMPORTANT! need to parse the text first before we print it. using the format given here https://github.com/DantSu/ESCPOS-ThermalPrinter-Android#formatted-text--syntax-guide
             */
            if(textToPrint==null||textToPrint.isEmpty())
                return;
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
                    UsbDevice usbDevice = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (usbManager != null && usbDevice != null) {
                            // YOUR PRINT CODE HERE
                            try {
                                EscPosPrinter printer = new EscPosPrinter(new UsbConnection(usbManager, usbDevice), 203, 48f, 32);
                                printer.printFormattedText(
                                        "[L]\n" +
                                                "[C]<u><font size='big'>"+textToPrint+"</font></u>\n" +
                                                "[L]\n" +
                                                "[C]================================\n"
                                );
                            } catch (EscPosConnectionException e) {
                                throw new RuntimeException(e);
                            } catch (EscPosEncodingException e) {
                                throw new RuntimeException(e);
                            } catch (EscPosBarcodeException e) {
                                throw new RuntimeException(e);
                            } catch (EscPosParserException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            }
        }
    };

    /**
     * print using BT printer
     * @param bluetoothConnection
     * @param text
     */
    public void printBluetooth(BluetoothConnection bluetoothConnection, String text){
        /*
            IMPORTANT! need to parse the text first before we print it. using the format given here https://github.com/DantSu/ESCPOS-ThermalPrinter-Android#formatted-text--syntax-guide
         */
        new Thread(new Runnable() {
            public void run() {
                try {
                    EscPosPrinter printer = new EscPosPrinter(bluetoothConnection, 203, 48f, 32);
                    printer
                            .printFormattedText(
                                    "[L]\n" +
                                            "[C]<u><font size='big'>"+text+"</font></u>\n" +
                                            "[L]\n" +
                                            "[C]================================\n"
                            );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * print using wifi (as for now not sure if we can detect other wifi printers other than epson)
     * @param name : printer name. we need to get the IP address only from the name
     * @param text : text to print
     */
    public void printWifi(String name, String text) {
        String[] names = name.split(" - ");
        String ipAddress = names[1];
        /*
            IMPORTANT! need to parse the text first before we print it. using the format given here https://github.com/DantSu/ESCPOS-ThermalPrinter-Android#formatted-text--syntax-guide
         */
        new Thread(new Runnable() {
            public void run() {
                try {
                    EscPosPrinter printer = new EscPosPrinter(new TcpConnection(ipAddress.trim(), 9100, 1000), 203, 48f, 32);
                    printer
                            .printFormattedTextAndCut(
                                    "[L]\n" +
                                            "[C]<u><font size='big'>"+text+"</font></u>\n" +
                                            "[L]\n" +
                                            "[C]================================\n"
                            );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * print using epson usb printers.
     * @param text
     * @return
     */
    private boolean runPrintReceiptSequence(String text) {
        //first we need to create the receipt means we need to parse it using epson format by calling createReceiptData
        if (!createReceiptData(text)) {
            Toast.makeText(mContext, "Failed to create receipt data", Toast.LENGTH_SHORT).show();
            return false;
        }
        //once done we can print it by calling printData
        if (!printData()) {
            Toast.makeText(mContext, "Failed to print data", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    public static com.epson.epos2.printer.Printer mPrinter = null;
    String epsonTarget = null;

    /**
     * to init the printer before using it
     * @param target
     */
    private void initializeEpsonPrinter(String target){
        try {
            //IMPORTANT! might need change T88 to match with used printer model. need further test using another model if its compatible still by using T88
            mPrinter = new com.epson.epos2.printer.Printer(com.epson.epos2.printer.Printer.TM_T88, 0, mContext);
            if(target.startsWith("USB:")){
                epsonTarget = "USB:";
            }
            else
                epsonTarget = target;
        } catch (Epos2Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * to format the receipt using epson's syntax
     * @param text : our text
     * @return
     */
    private boolean createReceiptData(String text) {
        String method = "";
//        Bitmap logoData = BitmapFactory.decodeResource(getResources(), R.drawable.store);
        StringBuilder textData = new StringBuilder();
        final int barcodeWidth = 2;
        final int barcodeHeight = 100;

        if (mPrinter == null) {
            Toast.makeText(mContext, "Printer not connected", Toast.LENGTH_SHORT).show();
            return false;
        }

        /*
            IMPORTANT! need to parse the text first before we print it. using the format given here from examples
         */
        try {

//            if(mDrawer.isChecked()) {
//                method = "addPulse";
//                mPrinter.addPulse(com.epson.epos2.printer.Printer.PARAM_DEFAULT,
//                        com.epson.epos2.printer.Printer.PARAM_DEFAULT);
//            }

            method = "addTextAlign";
            mPrinter.addTextAlign(com.epson.epos2.printer.Printer.ALIGN_CENTER);

            method = "addImage";
//            mPrinter.addImage(logoData, 0, 0,
//                    logoData.getWidth(),
//                    logoData.getHeight(),
//                    com.epson.epos2.printer.Printer.COLOR_1,
//                    com.epson.epos2.printer.Printer.MODE_MONO,
//                    com.epson.epos2.printer.Printer.HALFTONE_DITHER,
//                    com.epson.epos2.printer.Printer.PARAM_DEFAULT,
//                    com.epson.epos2.printer.Printer.COMPRESS_AUTO);

            method = "addFeedLine";
            mPrinter.addFeedLine(1);
            textData.append(text+"\n");
//            textData.append("STORE DIRECTOR – John Smith\n");
//            textData.append("\n");
//            textData.append("7/01/07 16:58 6153 05 0191 134\n");
//            textData.append("ST# 21 OP# 001 TE# 01 TR# 747\n");
            textData.append("------------------------------\n");
//            method = "addText";
//            mPrinter.addText(textData.toString());
//            textData.delete(0, textData.length());
//
//            textData.append("TEST PRINT              9.99 R\n");
//            textData.append("------------------------------\n");
//            method = "addText";
//            mPrinter.addText(textData.toString());
//            textData.delete(0, textData.length());
//
//            textData.append("SUBTOTAL                160.38\n");
//            textData.append("TAX                      14.43\n");
//            method = "addText";
//            mPrinter.addText(textData.toString());
//            textData.delete(0, textData.length());
//
//            method = "addTextSize";
//            mPrinter.addTextSize(2, 2);
//            method = "addText";
//            mPrinter.addText("TOTAL    174.81\n");
//            method = "addTextSize";
//            mPrinter.addTextSize(1, 1);
//            method = "addFeedLine";
//            mPrinter.addFeedLine(1);
//
//            textData.append("CASH                    200.00\n");
//            textData.append("CHANGE                   25.19\n");
//            textData.append("------------------------------\n");
//            method = "addText";
//            mPrinter.addText(textData.toString());
//            textData.delete(0, textData.length());

//            textData.append("Purchased item total number\n");
//            textData.append("Sign Up and Save !\n");
//            textData.append("With Preferred Saving Card\n");
//            method = "addText";
//            mPrinter.addText(textData.toString());
//            textData.delete(0, textData.length());
//            method = "addFeedLine";
//            mPrinter.addFeedLine(2);
//
//            method = "addBarcode";
//            mPrinter.addBarcode("01209457",
//                    com.epson.epos2.printer.Printer.BARCODE_CODE39,
//                    com.epson.epos2.printer.Printer.HRI_BELOW,
//                    com.epson.epos2.printer.Printer.FONT_A,
//                    barcodeWidth,
//                    barcodeHeight);

            method = "addCut";
            mPrinter.addCut(com.epson.epos2.printer.Printer.CUT_FEED);
        }
        catch (Exception e) {
            e.printStackTrace();
            mPrinter.clearCommandBuffer();
//            ShowMsg.showException(e, method, mContext);
            return false;
        }

        textData = null;

        return true;
    }

    /**
     * to print using epson printer
     * @return
     */
    private boolean printData() {
        if (mPrinter == null) {
            Toast.makeText(mContext, "Printer not connected 1", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!connectPrinter()) {
            Toast.makeText(mContext, "Printer not connected 2", Toast.LENGTH_SHORT).show();
            mPrinter.clearCommandBuffer();
            return false;
        }

        try {
            mPrinter.sendData(com.epson.epos2.printer.Printer.PARAM_DEFAULT);
        }
        catch (Exception e) {
            e.printStackTrace();
            mPrinter.clearCommandBuffer();
            try {
                mPrinter.disconnect();
            }
            catch (Exception ex) {
                // Do nothing
                ex.printStackTrace();
            }
            return false;
        }
        return true;
    }

    /**
     * to connect with epson printer
     * @return
     */
    private boolean connectPrinter() {
        if (mPrinter == null) {
            Toast.makeText(mContext, "Printer not connected 3", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            Log.e("epsonTarget", epsonTarget);
            mPrinter.connect(epsonTarget, com.epson.epos2.printer.Printer.PARAM_DEFAULT);
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    // Required for rn built in EventEmitter Calls.
    @ReactMethod
    public void addListener(String eventName) {

    }

    @ReactMethod
    public void removeListeners(Integer count) {

    }
}
