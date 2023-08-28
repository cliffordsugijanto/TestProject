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
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.annotations.ReactProp;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

public class PrinterListReactViewManager extends SimpleViewManager {
    public static final String REACT_CLASS = "PrinterListReactViewManager";
    private ArrayList<Printer> mDataset = new ArrayList<>();
    private CustomRecyclerView mRecyclerView;
    private RecyclerView.LayoutManager mLayoutManager;
    private ListPrinterRecyclerAdapter mAdapter;
    Context mContext;

    public static com.epson.epos2.printer.Printer mPrinter = null;
    String epsonTarget = null;
    boolean isDiscoverStarted = false;
    String jsonTextToPrint = "";
    BillDataModel billData;

    @ReactProp(name = "dataText")
    public void setDataText(View view, @Nullable String jsonTextToPrint) {
        this.jsonTextToPrint = jsonTextToPrint;
        this.billData = new Gson().fromJson(jsonTextToPrint, new TypeToken<BillDataModel>() {}.getType());
    }

    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @UiThread
    public void addPrinter(Printer p){
        mDataset.add(p);
        mRecyclerView.setmRequestedLayout(false);
        mAdapter.notifyItemInserted(mDataset.size()-1);
    }

    @Override
    protected RecyclerView createViewInstance(@NonNull ThemedReactContext ctx) {
        mContext = ctx;

        mRecyclerView = new CustomRecyclerView(ctx);
        mAdapter = new ListPrinterRecyclerAdapter(ctx, mDataset, new ListPrinterRecyclerAdapter.ActionListener() {
            @Override
            public void onItemClick(Printer p) {
                print(p);
                Toast.makeText(ctx, p.name+" "+jsonTextToPrint, Toast.LENGTH_SHORT).show();
            }
        });
        mLayoutManager = new LinearLayoutManager(ctx.getCurrentActivity(), LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setAdapter(mAdapter);
        Toast.makeText(ctx, jsonTextToPrint, Toast.LENGTH_SHORT).show();
        refreshList();

        return mRecyclerView;
    }

    private void refreshList(){
        mDataset.clear();
        Log.e("refreshList", "refreshList1");
        BluetoothPrintersConnections btConnections = new BluetoothPrintersConnections();
        UsbPrintersConnections usbPrintersConnections = new UsbPrintersConnections(mContext);
        BluetoothConnection[] bluetoothConnections = btConnections.getList();
        Log.e("refreshList", "refreshList2");
        if (ActivityCompat.checkSelfPermission(mContext, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            Toast.makeText(mContext, "Activate permission for nearby device / bluetooth", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.e("refreshList", "refreshList4");
        if(bluetoothConnections!=null) {
            for (BluetoothConnection connection : bluetoothConnections) {
                Log.e("refreshList", "refreshList5");
                BluetoothDevice device = connection.getDevice();
//                mDataset.add
                addPrinter(new Printer(0, BLUETOOTH_TYPE, device.getName() + " (" + device.getAddress() + ")", connection));
            }
        }
        UsbConnection[] usbConnections = usbPrintersConnections.getList();
        if(usbConnections!=null){
            for(UsbConnection connection : usbConnections){
                Log.e("refreshList", "refreshList6");
                UsbDevice device = connection.getDevice();
//                mDataset.add(
                addPrinter(new Printer(1, USB_TYPE, device.getProductName() + " (" + device.getManufacturerName() + ")", connection));
            }
        }
//        mDataset.add(new Printer(1, USB_TYPE, "Test device" + " (" + "Tester" + ")"));

//        if(mAdapter!=null) {
//            mRecyclerView.setmRequestedLayout(false);
//            mAdapter.notifyDataSetChanged();
//        }

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
                        return;
                    }
                }
            }
        }

        try {
            Discovery.start(mContext,
                    mFilterOption,
                    new DiscoveryListener() {
                @UiThread
                @Override
                public void onDiscovery(DeviceInfo deviceInfo) {
//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
                            if(!deviceInfo.getIpAddress().isEmpty()){
//                                mDataset.add(
                                addPrinter(new Printer(2, WIFI_TYPE, deviceInfo.getDeviceName()+" "+deviceInfo.getIpAddress(), deviceInfo.getTarget()));
                            }
                            else {
                                addPrinter(new Printer(3, EPSON_TYPE, deviceInfo.getDeviceName() + " " + deviceInfo.getIpAddress(), deviceInfo.getTarget()));
                            }
//                            if(mAdapter!=null) {
//                                mRecyclerView.setmRequestedLayout(false);
//                                mAdapter.notifyDataSetChanged();
//                            }
//                        }
//                    });
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
                refreshList();
            } catch (Epos2Exception e1) {
                if (e1.getErrorStatus() != Epos2Exception.ERR_PROCESSING) {
                    return;
                }
            }
        }
    }

    private void print(Printer p){
        if(p.type.equals(USB_TYPE)){
            printUsb(p.usbConnection);
        }
        else if(p.type.equals(BLUETOOTH_TYPE)){
            printBluetooth(p.bluetoothConnection);
        }
        else if(p.type.equals(EPSON_TYPE)){
            runPrintReceiptSequence();
        }
        else if(p.type.equals(WIFI_TYPE)){
            testPrintWifi(p.name);
        }
    }

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    public void printUsb(UsbConnection usbConnection) {
        UsbManager usbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);
        if (usbConnection != null && usbManager != null) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(
                    mContext,
                    0,
                    new Intent(ACTION_USB_PERMISSION),
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ? PendingIntent.FLAG_MUTABLE : 0
            );
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            mContext.getApplicationContext().registerReceiver(this.usbReceiver, filter);
            usbManager.requestPermission(usbConnection.getDevice(), permissionIntent);
        }
    }

    public void printBluetooth(BluetoothConnection bluetoothConnection){
        new Thread(new Runnable() {
            public void run() {
                try {
                    EscPosPrinter printer = new EscPosPrinter(bluetoothConnection, 203, 48f, 32);
                    printer
                            .printFormattedText(
                                    "[L]\n" +
                                            "[C]<u><font size='big'>TESTING PRINT</font></u>\n" +
                                            "[L]\n" +
                                            "[C]================================\n" +
                                            "[L]\n" +
                                            "[L]<b>BEAUTIFUL SHIRT</b>[R]9.99e\n" +
                                            "[L]  + Size : S\n" +
                                            "[L]\n" +
                                            "[L]<b>AWESOME HAT</b>[R]24.99e\n" +
                                            "[L]  + Size : 57/58\n" +
                                            "[L]\n" +
                                            "[C]--------------------------------\n" +
                                            "[R]TOTAL PRICE :[R]34.98e\n" +
                                            "[R]TAX :[R]4.23e\n" +
                                            "[L]\n" +
                                            "[C]================================\n" +
                                            "[L]\n" +
                                            "[L]<font size='tall'>Customer :</font>\n" +
                                            "[L]Raymond DUPONT\n" +
                                            "[L]5 rue des girafes\n" +
                                            "[L]31547 PERPETES\n" +
                                            "[L]Tel : +33801201456\n" +
                                            "[L]\n"
                            );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void testPrintWifi(String name){
        new Thread(new Runnable() {
            public void run() {
                try {
                    EscPosPrinter printer = new EscPosPrinter(new TcpConnection(name.trim(), 9100, 1000), 203, 48f, 32);
                    printer
                            .printFormattedTextAndCut(
//                                    "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, getApplicationContext().getResources().getDrawableForDensity(R.drawable.logo, DisplayMetrics.DENSITY_MEDIUM)) + "</img>\n" +
                                    "[L]\n" +
                                            "[C]<u><font size='big'>"+billData.test+"</font></u>\n" +
                                            "[L]\n" +
                                            "[C]================================\n" +
                                            "[L]\n" +
                                            "[L]<b>BEAUTIFUL SHIRT</b>[R]9.99e\n" +
                                            "[L]  + Size : S\n" +
                                            "[L]\n" +
                                            "[L]<b>AWESOME HAT</b>[R]24.99e\n" +
                                            "[L]  + Size : 57/58\n" +
                                            "[L]\n" +
                                            "[C]--------------------------------\n" +
                                            "[R]TOTAL PRICE :[R]34.98e\n" +
                                            "[L]\n"
                            );
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private boolean runPrintReceiptSequence() {
        if (!createReceiptData()) {
            Toast.makeText(mContext, "Failed to create receipt data", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!printData()) {
            Toast.makeText(mContext, "Failed to print data", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    private boolean createReceiptData() {
        String method = "";
//        Bitmap logoData = BitmapFactory.decodeResource(getResources(), R.drawable.store);
        StringBuilder textData = new StringBuilder();
        final int barcodeWidth = 2;
        final int barcodeHeight = 100;

        if (mPrinter == null) {
            Toast.makeText(mContext, "Printer not connected", Toast.LENGTH_SHORT).show();
            return false;
        }

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
            textData.append("THE STORE 123 (555) 555 – 5555\n");
            textData.append("STORE DIRECTOR – John Smith\n");
            textData.append("\n");
            textData.append("7/01/07 16:58 6153 05 0191 134\n");
            textData.append("ST# 21 OP# 001 TE# 01 TR# 747\n");
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

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
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
//                                                "[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, getApplicationContext().getResources().getDrawableForDensity(R.drawable.ic_launcher_background, DisplayMetrics.DENSITY_MEDIUM))+"</img>\n" +
                                        "[L]\n" +
                                                "[C]<u><font size='big'>"+billData.test+"</font></u>\n" +
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
}
