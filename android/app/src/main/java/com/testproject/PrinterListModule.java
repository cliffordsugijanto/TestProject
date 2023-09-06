package com.testproject;

import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.nsd.NsdManager;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dantsu.escposprinter.EscPosPrinter;
import com.dantsu.escposprinter.EscPosPrinterSize;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection;
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections;
import com.dantsu.escposprinter.connection.tcp.TcpConnection;
import com.dantsu.escposprinter.connection.usb.UsbConnection;
import com.dantsu.escposprinter.connection.usb.UsbPrintersConnections;
import com.dantsu.escposprinter.exceptions.EscPosBarcodeException;
import com.dantsu.escposprinter.exceptions.EscPosConnectionException;
import com.dantsu.escposprinter.exceptions.EscPosEncodingException;
import com.dantsu.escposprinter.exceptions.EscPosParserException;
import com.dantsu.escposprinter.textparser.PrinterTextParserImg;
import com.epson.epos2.Epos2Exception;
import com.epson.epos2.discovery.DeviceInfo;
import com.epson.epos2.discovery.Discovery;
import com.epson.epos2.discovery.DiscoveryListener;
import com.epson.epos2.discovery.FilterOption;
import com.epson.epos2.printer.PrinterStatusInfo;
import com.epson.epos2.printer.ReceiveListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

/**
 * Native module to get list of available printers and print function
 * @author clifford
 */
public class PrinterListModule extends ReactContextBaseJavaModule implements ReceiveListener {
    private static final int DISCONNECT_INTERVAL = 500;//millseconds
    ReactApplicationContext mContext;

    //for epson case. if isDiscoverStarted==true, then we stop the discovery before start again
    boolean isDiscoverStarted;

    //list of printers, to get the reference from printer name
    List<Printer> printers = new ArrayList<>();
    public static final String USB_TYPE = "usb";
    public static final String BLUETOOTH_TYPE = "bt";
    public static final String WIFI_TYPE = "wifi";
    public static final String EPSON_TYPE = "epson";
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
            Toast.makeText(mContext, "Activate permission for nearby device to discover bluetooth printers", Toast.LENGTH_SHORT).show();
        }
        Log.e("refreshList", "refreshList4");
        if(bluetoothConnections!=null) {
            for (BluetoothConnection connection : bluetoothConnections) {
                Log.e("refreshList", "refreshList5");
                BluetoothDevice device = connection.getDevice();

                String printerName = device.getName()+" "+device.getAddress()+" ("+BT_PRINTER + ")";
                printers.add(new Printer(0, BLUETOOTH_TYPE, printerName, connection));
                WritableMap params = Arguments.createMap();
                params.putString("printerName",printerName);
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
                String printerName = device.getManufacturerName()+" "+device.getProductName()+" ("+device.getDeviceId()+") ("+USB_PRINTER + ")";
                printers.add(new Printer(1, USB_TYPE, printerName, connection));
                WritableMap params = Arguments.createMap();
                params.putString("printerName",printerName);
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
                        String printerName = deviceInfo.getIpAddress()+" ("+WIFI_EPSON_PRINTER + ")";
                        printers.add(new Printer(2, WIFI_TYPE, printerName, deviceInfo.getTarget()));
                        WritableMap params = Arguments.createMap();
                        params.putString("printerName",printerName);
                        sendEventToReactFromAndroid(mContext, "PrinterEvent",params);
                    }
                    else {
                        String printerName = "Epson "+deviceInfo.getDeviceName()+" ("+deviceInfo.getTarget()+") ("+EPSON_PRINTER + ")";
                        printers.add(new Printer(3, EPSON_TYPE, printerName, deviceInfo.getTarget()));
                        WritableMap params = Arguments.createMap();
                        params.putString("printerName", printerName);
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
     * @param text : the json data that will be printed
     */
    @ReactMethod
    public void print(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String printerName = obj.getString("printerName"); //the printer name we got from getListOfPrinters

            for (Printer p : printers) {
                //we match it with the printers list, then get its details for print purposes
                if (p.name.equals(printerName)) {
                    if (p.type.equals(USB_TYPE)) {
                        printUsb(p.usbConnection, text);
                    } else if (p.type.equals(BLUETOOTH_TYPE)) {
                        printBluetooth(p.bluetoothConnection, text);
                    } else if (p.type.equals(EPSON_TYPE)) {
                        if (p.target != null) {
                            //initialize the printer first
                            initializeEpsonPrinter(p.target);
                        }
                        //then print the texts
                        runPrintReceiptSequence(text);
                    } else if (p.type.equals(WIFI_TYPE)) {
                        printWifi(p.name, text);
                    }
                }
            }
        }
        catch(JSONException e){
            Toast.makeText(mContext, "Print error", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
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
        @RequiresApi(api = Build.VERSION_CODES.O)
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
                            try {
                                JSONObject obj = new JSONObject(textToPrint);
                                String data = obj.getString("data");
                                String extraData = obj.getString("extraData");
                                JSONObject extraDataJson = new JSONObject(extraData);
                                boolean isOrder = obj.getString("printType").equals("order");
                                String imgLogo = "";
                                if(!isOrder)
                                    imgLogo = extraDataJson.getString("invLogo");

                                EscPosPrinter printer = new EscPosPrinter(new UsbConnection(usbManager, usbDevice), 203, 48f, 32);
                                printer.printFormattedText(
                                        (isOrder ? "": parseImageHex(printer, imgLogo, true))
                                                + (isOrder ? parseJsonFormatOrderNonEpson(data, extraData) : parseJsonFormatBillNonEpson(data, extraData))
                                );
                            } catch (EscPosConnectionException e) {
                                Toast.makeText(mContext, "Connection error", Toast.LENGTH_SHORT).show();
//                                throw new RuntimeException(e);
                            } catch (EscPosEncodingException e) {
                                Toast.makeText(mContext, "Encoding error", Toast.LENGTH_SHORT).show();
//                                throw new RuntimeException(e);
                            } catch (EscPosBarcodeException e) {
                                Toast.makeText(mContext, "Barcode error", Toast.LENGTH_SHORT).show();
//                                throw new RuntimeException(e);
                            } catch (EscPosParserException e) {
                                Toast.makeText(mContext, "Parser error", Toast.LENGTH_SHORT).show();
//                                throw new RuntimeException(e);
                            } catch (JSONException e) {
                                Toast.makeText(mContext, "JSON error", Toast.LENGTH_SHORT).show();
//                                throw new RuntimeException(e);
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
     * @param textToPrint
     */
    public void printBluetooth(BluetoothConnection bluetoothConnection, String textToPrint){
        /*
            IMPORTANT! need to parse the text first before we print it. using the format given here https://github.com/DantSu/ESCPOS-ThermalPrinter-Android#formatted-text--syntax-guide
         */
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject obj = new JSONObject(textToPrint);
                    String data = obj.getString("data");
                    String extraData = obj.getString("extraData");
                    JSONObject extraDataJson = new JSONObject(extraData);
                    boolean isOrder = obj.getString("printType").equals("order");
                    String imgLogo = "";
                    if(!isOrder)
                        imgLogo = extraDataJson.getString("invLogo");

                    EscPosPrinter printer = new EscPosPrinter(bluetoothConnection, 203, 48f, 32);
                    printer
                            .printFormattedText(
                                    (isOrder ? "": parseImageHex(printer, imgLogo, true))
                                            + (isOrder ? parseJsonFormatOrderNonEpson(data, extraData) : parseJsonFormatBillNonEpson(data, extraData))
                            );
                } catch (Exception e) {
                    Toast.makeText(mContext, "BT print error", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private String parseImageHex(EscPosPrinterSize printer, String imgLogo, boolean isLogo){
        String[] logo = imgLogo.split(",");
        String base64 = logo[1].split("\"")[0];
        if(isLogo) {
//            String img = "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";
//            Log.e("img", img.length() + " " + img.substring(img.length()-13, img.length()));
//            Log.e("base64", base64.length() + " " + base64.substring(base64.length()-13, base64.length()));
//            Log.e("compare", base64.equals(img)+"");

            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

            Bitmap imageWithBG = Bitmap.createBitmap(decodedByte.getWidth(), decodedByte.getHeight(),decodedByte.getConfig());  // Create another image the same size
            imageWithBG.eraseColor(Color.WHITE);  // set its background to white, or whatever color you want
            Canvas canvas = new Canvas(imageWithBG);  // create a canvas to draw on the new image
            canvas.drawBitmap(decodedByte, 0f, 0f, null); // draw old image on the background
            decodedByte.recycle();  // clear out old image

            int width = imageWithBG.getWidth(), height = imageWithBG.getHeight();
            StringBuilder textImg = new StringBuilder();

            float aspectRatio = width / (float) height;
            int newHeight = 100;
            int newWidth = Math.round(newHeight * aspectRatio);

            Bitmap bmp = Bitmap.createScaledBitmap(imageWithBG, newWidth, newHeight, false);
            textImg.append("[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, bmp) + "</img>\n");

            imageWithBG.recycle();
            bmp.recycle();
            return textImg.toString();
        }
        else{
            byte[] decodedString = Base64.decode(base64, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            int width = decodedByte.getWidth(), height = decodedByte.getHeight();
//            Log.e("width height", width+" "+height);
            StringBuilder textImg = new StringBuilder();

            float aspectRatio = width / (float) height;
            int newWidth = 480;
            int newHeight = Math.round(newWidth / aspectRatio);

            Bitmap bmp = Bitmap.createScaledBitmap(decodedByte, newWidth, newHeight, false);

            for(int y = 0; y < height; y += 256) {
//                Log.e("check height", "y + 256 >= newHeight -> "+(y + 256 >= newHeight)+" newHeight - y ->"+(newHeight - y));
                Bitmap bitmap = Bitmap.createBitmap(bmp, 0, y, newWidth, (y + 256 >= newHeight) ? newHeight - y : 256);
                textImg.append("[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap) + "</img>\n");
                bitmap.recycle();
                if(y + 256 >= newHeight)
                    break;
            }
            decodedByte.recycle();
            bmp.recycle();
            return textImg.toString();
        }
    }

    private String parseJsonFormatOrderNonEpson(String json, String extraData) throws JSONException {
        String output = "";
        JSONObject obj = new JSONObject(json);
        JSONObject extra = new JSONObject(extraData);

        boolean isCompact = extra.getBoolean("compact");
        String lang = extra.getString("bill_lang");

        HashMap<String, String> textLabel = getTextHashMap(lang);

        int ordersId = obj.getInt("orders_id");
        String billId = obj.getString("c_bill_id");
        output += "[L]<font size='big'>Order no : "+ordersId+"</font>\n";
        output += "[L]\n";
        output += "[L]<font size='normal'>"+textLabel.get("billnolabel")+": "+billId+"</font>\n";

        String posBillDate = obj.getString("created_at");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat formatter2 = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        try {
            Date billDate = formatter.parse(posBillDate);
            posBillDate = formatter2.format(billDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        output += "[L]<font size='normal'>"+textLabel.get("billdatelabel")+": "+posBillDate+"</font>\n" +
                addLine(isCompact);


        JSONArray items = new JSONArray(obj.getString("listorders"));
        for(int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.getString("name");
            String table = item.getString("table_name");
            int qty = item.getInt("quantity");
            output += "[L]<b>" + qty + "x</b>[R] Table :" + table + "\n";
            output += "[L]<b>" + name + "</b>\n";

            if(!item.toString().contains("options\":null")) {
                JSONArray options = item.getJSONArray("options");
                for (int j = 0; j < options.length(); j++) {
                    JSONObject optItem = options.getJSONObject(j);
                    String optName = optItem.getString("name");
                    output += "[L]  + " + optName + "\n";
                }
            }

            String note = item.getString("productNotes");
            if(!note.isEmpty())
                output += "[L]  + "+textLabel.get("notes")+": "+ note + "\n";

            if(!isCompact)
                output += "[L]\n";
        }
        output += addLine(isCompact);

        return output;
    }

    private String parseJsonFormatBillNonEpson(String json, String extraData) throws JSONException {

        String output = "";
        JSONObject obj = new JSONObject(json);
        JSONObject extra = new JSONObject(extraData);

        output += "[L]\n";
        String header = extra.getString("invHeader");
        output += "[C]<font size='tall'>"+header.substring(2, header.length()-2)+"</font>\n";

        int isNumberFormat = extra.getString("isNumberFormat").equals("1") ? 1 : 0;
        boolean isCompact = extra.getBoolean("compact");
        String lang = extra.getString("bill_lang");

        HashMap<String, String> textLabel = getTextHashMap(lang);

        //data section
        String billId = obj.getString("c_bill_id");
        String posBillDate = obj.getString("pos_bill_date");
        String posPaidDate = obj.getString("pos_paid_bill_date");
        String posCashier = obj.getString("cashier");
        String posCustomerName = obj.getString("customer_name");
        SimpleDateFormat formatter = new SimpleDateFormat("E MMMM dd yyyy HH:mm:ss");
        SimpleDateFormat formatter2 = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        try {
            Date billDate = formatter.parse(posBillDate);
            Date paidDate = formatter.parse(posPaidDate);
            posBillDate = formatter2.format(billDate);
            posPaidDate = formatter2.format(paidDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        int div = obj.getInt("div");

        output += "[L]<font size='normal'>"+textLabel.get("billnolabel")+": "+billId+"</font>\n" +
                "[L]<font size='normal'>"+textLabel.get("billdatelabel")+": "+posBillDate+"</font>\n" +
                "[L]<font size='normal'>"+textLabel.get("paid")+": "+posPaidDate+"</font>\n" +
                "[L]<font size='normal'>"+textLabel.get("billcashierlabel")+": "+posCashier+"</font>\n" +
                "[L]<font size='normal'>"+textLabel.get("cbilllabel")+": "+posCustomerName+"</font>\n" +
                addLine(isCompact);

        JSONArray items = new JSONArray(obj.getString("order_collection"));
        for(int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String name = item.getString("name");
            int qty = item.getInt("quantity");
            int price = item.getInt("price");
            output += "[L]<b>" + qty + "x</b>[L]@" + commaSeparateNumber((price / div)+"",isNumberFormat,div)+ "[R]" + commaSeparateNumber(((price / div) * qty)+"",isNumberFormat,div) + "\n";
            output += "[L]<b>" + name + "</b>\n";

            if(!item.toString().contains("options\":null")) {
                JSONArray options = item.getJSONArray("options");
                for (int j = 0; j < options.length(); j++) {
                    JSONObject optItem = options.getJSONObject(j);
                    String optName = optItem.getString("name");
                    int optPrice = optItem.getInt("price");
                    output += "[L]  + " + optName + " (" + commaSeparateNumber(optPrice + "", isNumberFormat, div) + ")\n";
                }
            }
            String note = item.getString("productNotes");
            if(!note.isEmpty())
                output += "[L]  + "+textLabel.get("notes")+": "+ note + "\n";

            if(!isCompact)
                output += "[L]\n";
        }
        output += addLine(isCompact);

        double discount = obj.getDouble("bill_discount");
        if(discount > 0) {
            int totalDisc = obj.getInt("total_discount");
            output += "[R]"+textLabel.get("discount").toUpperCase()+" "+Double.toString(discount).replaceAll("\\.?0*$", "") + "%: [R]-"+commaSeparateNumber(totalDisc+"",isNumberFormat,div)+ "\n";
            output += addLine(isCompact);
        }

        int subtotal = obj.getInt("totalafterdiscount");
        int totalbeforetax = obj.getInt("totalbeforetax");
        int totalaftertax = obj.getInt("totalaftertax");
        int totalafterrounding = obj.getInt("totalafterrounding");

        if(subtotal<totalbeforetax){
            output += "[R]"+textLabel.get("subtotal").toUpperCase()+": [R]"+commaSeparateNumber(subtotal+"",isNumberFormat,div)+"\n";
            double servicefee = obj.getDouble("servicefee");
            if(servicefee>0) {
                int totalservicefee = obj.getInt("totalservicefee");
                output += "[R]"+textLabel.get("servicefee").toUpperCase()+" "+ Double.toString(servicefee).replaceAll("\\.?0*$", "") + "%: [R]" + commaSeparateNumber(totalservicefee+"",isNumberFormat,div) + "\n";
            }
            double gratuity = obj.getDouble("gratuity");
            if(gratuity>0) {
                int totalgratuity = obj.getInt("totalgratuity");
                output += "[R]"+textLabel.get("gratuity").toUpperCase()+" "+ Double.toString(gratuity).replaceAll("\\.?0*$", "") + "%: [R]" + commaSeparateNumber(totalgratuity+"",isNumberFormat,div) + "\n";
            }
            output += addLine(isCompact);
        }
        if(totalbeforetax<totalaftertax){
            output += "[R]"+textLabel.get("total").toUpperCase()+": [R]"+commaSeparateNumber(totalbeforetax+"",isNumberFormat,div)+"\n";
            double vat = obj.getDouble("vat");
            if(vat>0){
                int totalvat = obj.getInt("totalvat");
                output += "[R]"+textLabel.get("tax").toUpperCase()+" "+ Double.toString(vat).replaceAll("\\.?0*$", "") + "%: [R]" + commaSeparateNumber(totalvat+"",isNumberFormat,div) + "\n";
            }
            output += addLine(isCompact);
        }
        if(totalaftertax<totalafterrounding){
            output += "[R]"+textLabel.get("totalincltax").toUpperCase()+": [R]"+commaSeparateNumber(totalaftertax+"",isNumberFormat,div)+"\n";
            double rounding = obj.getDouble("rounding");
            if(rounding>0){
                output += "[R]"+textLabel.get("rounding").toUpperCase()+": [R]"+ commaSeparateNumber(rounding+"",isNumberFormat,div) + "\n";
            }
            output += addLine(isCompact);
        }
        output += "[R]"+textLabel.get("total").toUpperCase()+": [R]"+commaSeparateNumber(totalafterrounding+"",isNumberFormat,div)+"\n";
        String payment_method = obj.getString("payment_method");
        int amount_paid = obj.getInt("amount_paid");
        if(!payment_method.isEmpty() && amount_paid>0) {
            output += "[R]" + payment_method.toUpperCase() + " : [R]" + commaSeparateNumber(amount_paid+"",isNumberFormat,div) + "\n";
        }
        int returned = obj.getInt("return");
        if(returned>0){
            output += "[R]"+textLabel.get("return").toUpperCase()+": [R]"+ commaSeparateNumber(returned+"",isNumberFormat,div) + "\n";
        }
        output += addLine(isCompact);

        output += "[L]"+textLabel.get("poweredByRebillPos")+"\n";

        String invFooter = extra.getString("invFooter");
        if(invFooter.equals("[\"\"]"))
            invFooter = "";
        output += "[C]<font size='tall'>"+invFooter+"</font>\n";
        if(discount > 0) {
            output += "[R]*"+textLabel.get("subject_to")+"\n";
        }

        return output;
    }

    private String addLine(boolean isCompact){
        if(isCompact){
            return "[C]--------------------------------\n";
        }
        else{
            return "[C]<u>                                </u>\n" +
                    "[L]\n";
        }
    }

    private HashMap getTextHashMap(String lang){
        switch (lang) {
            case "en":
                HashMap<String, String> en = new HashMap<String, String>();
                en.put("billnolabel", "Bill No");
                en.put("billdatelabel", "Created");
                en.put("billcashierlabel", "Cashier");
                en.put("cbilllabel", "Customer Name");
                en.put("pointsearned", "Points Earned,");
                en.put("used", "Used,");
                en.put("saldo", "Saldo,");
                en.put("item", "Item");
                en.put("qty", "Qty");
                en.put("price", "Price");
                en.put("subtotal", "Subtotal");
                en.put("discount", "Discount");
                en.put("loyaltydiscount", "Loyalty Discount");
                en.put("servicefee", "Service Fee");
                en.put("gratuity", "Gratuity");
                en.put("tax", "Tax");
                en.put("total", "Total");
                en.put("totalincltax", "Total incl. Tax");
                en.put("splitpay", "Split Pay");
                en.put("rounding", "Rounding");
                en.put("paid", "Paid");
                en.put("unpaidBill", "UNPAID BILL");
                en.put("guest", "Guest");
                en.put("merchant", "Merchant");
                en.put("poweredByRebillPos", "Powered by ReBill POS");
                en.put("subject_to", "Subject to eligibilities and maximum caps");
                en.put("notes", "Notes");
                en.put("return", "Return");
                return en;
            case "id":
                HashMap<String, String> id = new HashMap<String, String>();
                id.put("billnolabel", "No. Tagihan");
                id.put("billdatelabel", "Dibuat");
                id.put("billcashierlabel", "Kasir");
                id.put("cbilllabel", "Nama Pelanggan");
                id.put("pointsearned", "Poin didapat,");
                id.put("used", "Digunakan,");
                id.put("saldo", "Saldo,");
                id.put("item", "Item");
                id.put("qty", "Jumlah");
                id.put("price", "Harga");
                id.put("subtotal", "Subtotal");
                id.put("discount", "Diskon");
                id.put("loyaltydiscount", "Diskon Loyalitas");
                id.put("servicefee", "Biaya Layanan");
                id.put("gratuity", "Gratifikasi");
                id.put("tax", "Pajak");
                id.put("total", "Total");
                id.put("totalincltax", "Total termasuk Pajak");
                id.put("splitpay", "Pembayaran Terpisah");
                id.put("rounding", "Pembulatan");
                id.put("paid", "Dibayar");
                id.put("unpaidBill", "TAGIHAN BELUM DIBAYAR");
                id.put("guest", "Pengunjung");
                id.put("merchant", "Pedagang");
                id.put("poweredByRebillPos", "Didukung oleh ReBill POS");
                id.put("subject_to", "Syarat dan ketentuan berlaku");
                id.put("notes", "Catatan");
                id.put("return", "Kembali");
                return id;
            case "my":
                HashMap<String, String> my = new HashMap<String, String>();
                my.put("billnolabel", "No. Bil");
                my.put("billdatelabel", "Dicipta");
                my.put("billcashierlabel", "Cashier");
                my.put("cbilllabel", "Nama Pelanggan");
                my.put("pointsearned", "Mata Diperolehi,");
                my.put("used", "Terpakai,");
                my.put("saldo", "Saldo,");
                my.put("item", "Item");
                my.put("qty", "Kuantiti");
                my.put("price", "Harga");
                my.put("subtotal", "Jumlah Kecil");
                my.put("discount", "Diskaun");
                my.put("loyaltydiscount", "Diskaun Kesetiaan");
                my.put("servicefee", "Yuran perkhidmatan");
                my.put("gratuity", "Ganjaran");
                my.put("tax", "Cukai");
                my.put("total", "Total");
                my.put("totalincltax", "Jumlah termasuk Cukai");
                my.put("splitpay", "Pisah Gaji");
                my.put("rounding", "Pembulatan");
                my.put("paid", "Dibayar");
                my.put("unpaidBill", "Bil Tidak Dibayar");
                my.put("guest", "Tetamu");
                my.put("merchant", "saudagar");
                my.put("poweredByRebillPos", "Dikuasakan ReBill POS");
                my.put("subject_to", "Tertakluk kepada kelayakan dan had maksimum");
                my.put("notes", "Nota");
                my.put("return", "Pulangan");
                return my;
            case "de":
                HashMap<String, String> de = new HashMap<String, String>();
                de.put("billnolabel", "RechnungsNr.");
                de.put("billdatelabel", "Erstellt");
                de.put("billcashierlabel", "Kassierer");
                de.put("cbilllabel", "Kundenname");
                de.put("pointsearned", "Punkte erhalten,");
                de.put("used", "Gebraucht,");
                de.put("saldo", "Saldo,");
                de.put("item", "Artikel");
                de.put("qty", "Menge");
                de.put("price", "Preis");
                de.put("subtotal", "Zwischensumme");
                de.put("discount", "Rabatt");
                de.put("loyaltydiscount", "Treuerabatt");
                de.put("servicefee", "Servicegebühr");
                de.put("gratuity", "Trinkgeld");
                de.put("tax", "Steuer");
                de.put("total", "Summe");
                de.put("totalincltax", "Gesamt inkl. Steuer");
                de.put("splitpay", "Teilzahlung");
                de.put("rounding", "Runden");
                de.put("paid", "Bezahlt");
                de.put("unpaidBill", "Unbezahlte Rechnung");
                de.put("guest", "Gast");
                de.put("merchant", "Händler");
                de.put("poweredByRebillPos", "Unterstützt von ReBill POS");
                de.put("subject_to", "Abhängig von Berechtigungen und Höchstgrenzen");
                de.put("notes", "Notizen");
                de.put("return", "Rückgabe");
                return de;
            case "fr":
                HashMap<String, String> fr = new HashMap<String, String>();
                fr.put("billnolabel", "No. de Facture");
                fr.put("billdatelabel", "Créé");
                fr.put("billcashierlabel", "La Caissière");
                fr.put("cbilllabel", "Nom du Client");
                fr.put("pointsearned", "Points gagnés,");
                fr.put("used", "Utilisé,");
                fr.put("saldo", "Saldo,");
                fr.put("item", "Article");
                fr.put("qty", "Qté");
                fr.put("price", "Prix");
                fr.put("subtotal", "Total");
                fr.put("discount", "Remise");
                fr.put("loyaltydiscount", "Remise fidélité");
                fr.put("servicefee", "Frais de Service");
                fr.put("gratuity", "Pourboire");
                fr.put("tax", "Impôt");
                fr.put("total", "Total");
                fr.put("totalincltax", "Total incl. Impôt");
                fr.put("splitpay", "Rémunération Fractionnée");
                fr.put("rounding", "Arrondissement");
                fr.put("paid", "Payé");
                fr.put("unpaidBill", "Facture impayée");
                fr.put("guest", "invité");
                fr.put("merchant", "Marchand");
                fr.put("poweredByRebillPos", "Propulsé par ReBill POS");
                fr.put("subject_to", "Sous réserve d'éligibilités et de plafonds maximaux");
                fr.put("notes", "Remarques");
                fr.put("return", "Retour");
                return fr;
            case "th":
                HashMap<String, String> th = new HashMap<String, String>();
                th.put("billnolabel", "เลขที่บิล");
                th.put("billdatelabel", "สร้าง");
                th.put("billcashierlabel", "แคชเชียร์");
                th.put("cbilllabel", "ชื่อลูกค้า");
                th.put("pointsearned", "คะแนนที่ได้รับ,");
                th.put("used", "ใช้แล้ว,");
                th.put("saldo", "ซัลโด,");
                th.put("item", "สิ่งของ");
                th.put("qty", "จำนวน");
                th.put("price", "ราคา");
                th.put("subtotal", "ยอดรวม");
                th.put("discount", "การลดราคา");
                th.put("loyaltydiscount", "การลดราคา ความภักดี");
                th.put("servicefee", "ค่าบริการ");
                th.put("gratuity", "บำเหน็จ");
                th.put("tax", "ภาษี");
                th.put("total", "ทั้งหมด");
                th.put("totalincltax", "รวมทั้งหมด ภาษี");
                th.put("splitpay", "แบ่งจ่าย");
                th.put("rounding", "การปัดเศษ");
                th.put("paid", "จ่ายเงิน");
                th.put("unpaidBill", "บิลยังไม่ได้ชำระเงิน");
                th.put("guest", "แขก");
                th.put("merchant", "พ่อค้า");
                th.put("poweredByRebillPos", "ขับเคลื่อนโดย ReBill POS");
                th.put("subject_to", "ขึ้นอยู่กับคุณสมบัติและการจำกัดสูงสุด");
                th.put("notes", "บันทึก");
                th.put("return", "คืน");
                return th;
            case "vn":
                HashMap<String, String> vn = new HashMap<String, String>();
                vn.put("billnolabel", "Số hóa đơn");
                vn.put("billdatelabel", "Tạo");
                vn.put("billcashierlabel", "Thu ngân");
                vn.put("cbilllabel", "Tên khách hàng");
                vn.put("pointsearned", "Điểm kiếm được,");
                vn.put("used", "Đã sử dụng,");
                vn.put("saldo", "Saldo,");
                vn.put("item", "Mặt hàng");
                vn.put("qty", "Số lượng");
                vn.put("price", "Giá bán");
                vn.put("subtotal", "Tổng phụ");
                vn.put("discount", "Miễn giảm");
                vn.put("loyaltydiscount", "Giảm giá lòng trung thành");
                vn.put("servicefee", "Phí dịch vụ");
                vn.put("gratuity", "giải thưởng");
                vn.put("tax", "Thuế");
                vn.put("total", "Tổng cộng");
                vn.put("totalincltax", "Tổng bao gồm Thuế");
                vn.put("splitpay", "Thanh toán nhiều lần");
                vn.put("rounding", "Làm tròn");
                vn.put("paid", "Đã thanh toán");
                vn.put("unpaidBill", "Hóa đơn chưa thanh toán");
                vn.put("guest", "Khách mời");
                vn.put("merchant", "Thương gia");
                vn.put("poweredByRebillPos", "Được động cơ bởi ReBill POS");
                vn.put("subject_to", "Dưới sự thể hiện của điều kiện và giới hạn tối đa");
                vn.put("notes", "Ghi chú");
                vn.put("return", "Trả hàng");
                return vn;
            case "es":
                HashMap<String, String> es = new HashMap<String, String>();
                es.put("billnolabel", "Proyecto de ley No.");
                es.put("billdatelabel", "Creado");
                es.put("billcashierlabel", "Cajero");
                es.put("cbilllabel", "Nombre de cliente");
                es.put("pointsearned", "Puntos ganados,");
                es.put("used", "Usó,");
                es.put("saldo", "Saldó,");
                es.put("item", "Articulo");
                es.put("qty", "Cantidad");
                es.put("price", "Precio");
                es.put("subtotal", "Total Parcial");
                es.put("discount", "Descuento");
                es.put("loyaltydiscount", "Descuento por fidelidad");
                es.put("servicefee", "Tarifa de Servicio");
                es.put("gratuity", "Gratuidad");
                es.put("tax", "Impuesto");
                es.put("total", "Total");
                es.put("totalincltax", "Total incl. Impuesto");
                es.put("splitpay", "Pago Dividido");
                es.put("rounding", "Redondeo");
                es.put("paid", "Pagado");
                es.put("unpaidBill", "Factura impagada");
                es.put("guest", "Invitado");
                es.put("merchant", "Comerciante");
                es.put("poweredByRebillPos", "Impulsado por ReBill POS");
                es.put("subject_to", "Sujeto a elegibilidades y límites máximos");
                es.put("notes", "Notas");
                es.put("return", "Devolución");
                return es;
            case "ph":
                HashMap<String, String> ph = new HashMap<String, String>();
                ph.put("billnolabel", "Bill No.");
                ph.put("billdatelabel", "Nilikha");
                ph.put("billcashierlabel", "Cashier");
                ph.put("cbilllabel", "Pangalan ng Customer");
                ph.put("pointsearned", "Nagkamit puntos,");
                ph.put("used", "Ginamit,");
                ph.put("saldo", "Saldo,");
                ph.put("item", "Item");
                ph.put("qty", "Qty");
                ph.put("price", "Presyo");
                ph.put("subtotal", "Subtotal");
                ph.put("discount", "Diskwento");
                ph.put("loyaltydiscount", "Diskwento sa katapatan");
                ph.put("servicefee", "Kabayaran sa Serbisyo");
                ph.put("gratuity", "Pabuya");
                ph.put("tax", "Buwis");
                ph.put("total", "Kabuuan");
                ph.put("totalincltax", "Kabuuang kasama Buwis");
                ph.put("splitpay", "Hatiin ang Bayad");
                ph.put("rounding", "Pagpapapuntos");
                ph.put("paid", "Bayad na");
                ph.put("unpaidBill", "Hindi nabayaran na bill");
                ph.put("guest", "Bisita");
                ph.put("merchant", "Mangangalakal");
                ph.put("poweredByRebillPos", "Pinalakas ng ReBill POS");
                ph.put("subject_to", "Nakatali sa mga kwalipikasyon at maximum na limitasyon");
                ph.put("notes", "Mga Tala");
                ph.put("return", "Babalik");
                return ph;
            case "it":
                HashMap<String, String> it = new HashMap<String, String>();
                it.put("billnolabel", "Disegno di legge No.");
                it.put("billdatelabel", "Creato");
                it.put("billcashierlabel", "Cassiere");
                it.put("cbilllabel", "Nome del Cliente");
                it.put("pointsearned", "Punti guadagnati");
                it.put("used", "Usato,");
                it.put("saldo", "Saldo,");
                it.put("item", "Articolo");
                it.put("qty", "Qtà");
                it.put("price", "Prezzo");
                it.put("subtotal", "Totale Parziale");
                it.put("discount", "Sconto");
                it.put("loyaltydiscount", "Sconto fedeltà");
                it.put("servicefee", "Costo del Servizio");
                it.put("gratuity", "Gratuità");
                it.put("tax", "Imposta");
                it.put("total", "Totale");
                it.put("totalincltax", "Totale incl. Imposta");
                it.put("splitpay", "Dividi la Paga");
                it.put("rounding", "Arrotondamento");
                it.put("paid", "Pagato");
                it.put("unpaidBill", "Fattura non pagata");
                it.put("guest", "Ospite");
                it.put("merchant", "Mercante");
                it.put("poweredByRebillPos", "Alimentato da ReBill POS");
                it.put("subject_to", "Soggetto a condizioni di eleggibilità e massimali");
                it.put("notes", "Note");
                it.put("return", "Reso");
                return it;
            case "pt":
                HashMap<String, String> pt = new HashMap<String, String>();
                pt.put("billnolabel", "Conta Nº");
                pt.put("billdatelabel", "Criado");
                pt.put("billcashierlabel", "Caixa");
                pt.put("cbilllabel", "Nome Do Cliente");
                pt.put("pointsearned", "Pontos ganhos,");
                pt.put("used", "Usado,");
                pt.put("saldo", "Saldo,");
                pt.put("item", "Item");
                pt.put("qty", "Qtd");
                pt.put("price", "Preço");
                pt.put("subtotal", "Subtotal");
                pt.put("discount", "Desconto");
                pt.put("loyaltydiscount", "Desconto de fidelidade");
                pt.put("servicefee", "Taxa de serviço");
                pt.put("gratuity", "Gratificação");
                pt.put("tax", "Imposto");
                pt.put("total", "Total");
                pt.put("totalincltax", "Total incl. Imposto");
                pt.put("splitpay", "Pagamento Dividido");
                pt.put("rounding", "Arredondamento");
                pt.put("paid", "Pago");
                pt.put("unpaidBill", "Conta não paga");
                pt.put("guest", "Convidado");
                pt.put("merchant", "Comerciante");
                pt.put("poweredByRebillPos", "Alimentado por ReBill POS");
                pt.put("subject_to", "Sujeito a elegibilidades e limites máximos");
                pt.put("notes", "Notas");
                pt.put("return", "Devolução");
                return pt;
            default:
                return null;
        }
    }

    private String commaSeparateNumber(String input, int isNumberFormat, int currencyDecimal) {
        boolean isDecimal = false;
        Log.e("val input", input);
        double val = Double.parseDouble(input);
        Log.e("val double", String.valueOf(val));
        if (currencyDecimal != 0) {
            DecimalFormat df = new DecimalFormat();
            df.setMaximumFractionDigits(2);
            df.setGroupingUsed(false);
            val = Double.parseDouble(df.format(val));
        }
        char dot = '.';
        char comma = ',';
        // remove sign if negative
        int sign = 1;
        if (val < 0) {
            sign = -1;
            val = -val;
        }

        // trim the number decimal point if it exists, add grouping separator
        String num = String.valueOf(val);
        if(isNumberFormat == 1) {
            DecimalFormat df = new DecimalFormat("#,###.00");
            df.setMaximumFractionDigits(0);
            num = df.format(val);
            num = num.replace(",",".").replace("\u00A0", "");
        }
        else{
            DecimalFormat df = new DecimalFormat("#,###.##");
            df.setMinimumFractionDigits(2);
            df.setMaximumFractionDigits(2);
            num = df.format(val);
        }
        // return result with - sign if negative
        return sign < 0 ? '-' + num : num;
    }


    /**
     * print using wifi (as for now not sure if we can detect other wifi printers other than epson)
     * @param name : printer name. we need to get the IP address only from the name
     * @param textToPrint : text to print
     */
    public void printWifi(String name, String textToPrint) {
        String[] names = name.split("\\(");
        String ipAddress = names[0];
        /*
            IMPORTANT! need to parse the text first before we print it. using the format given here https://github.com/DantSu/ESCPOS-ThermalPrinter-Android#formatted-text--syntax-guide
         */
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject obj = new JSONObject(textToPrint);
                    String data = obj.getString("data");
                    String extraData = obj.getString("extraData");
                    JSONObject extraDataJson = new JSONObject(extraData);
                    boolean isOrder = obj.getString("printType").equals("order");
                    String imgLogo = "";
                    if(!isOrder)
                        imgLogo = extraDataJson.getString("invLogo");

                    EscPosPrinter printer = new EscPosPrinter(new TcpConnection(ipAddress.trim(), 9100, 1000), 203, 48f, 32);
                    printer
                            .printFormattedTextAndCut(
                                    (isOrder ? "": parseImageHex(printer, imgLogo, true))
                                            + (isOrder ? parseJsonFormatOrderNonEpson(data, extraData) : parseJsonFormatBillNonEpson(data, extraData))
                            );
                } catch (Exception e) {
                    Toast.makeText(mContext, "Wifi print error", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * print using epson usb printers.
     * @param
     * @return
     */
    private boolean runPrintReceiptSequence(String json) {
        try {
            JSONObject obj = new JSONObject(json);
            boolean isOrder = obj.getString("printType").equals("order");
            String data = obj.getString("data");
            String extraData = obj.getString("extraData");
            //first we need to create the receipt means we need to parse it using epson format by calling createReceiptData
            if (isOrder && !createReceiptOrderData(data,extraData)){
                Toast.makeText(mContext, "Failed to create order data", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (!isOrder && !createReceiptData(data, extraData)) {
                Toast.makeText(mContext, "Failed to create receipt data", Toast.LENGTH_SHORT).show();
                return false;
            }

            //once done we can print it by calling printData
            if (!printData()) {
                Toast.makeText(mContext, "Failed to print data", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        catch(JSONException e){
            e.printStackTrace();
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
        finalizeObject();
        try {
            //IMPORTANT! might need change T88 to match with used printer model. need further test using another model if its compatible still by using T88
            mPrinter = new com.epson.epos2.printer.Printer(com.epson.epos2.printer.Printer.TM_T88, 0, mContext);
//            if(target.startsWith("USB:")){
//                epsonTarget = "USB:";
//            }
//            else
            epsonTarget = target;

            mPrinter.setReceiveEventListener(this);
        } catch (Epos2Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void finalizeObject() {
        if (mPrinter == null) {
            return;
        }

        mPrinter.setReceiveEventListener(null);

        mPrinter = null;
    }

    private void initializeEpsonWifiPrinter(String target){
        try {
            //IMPORTANT! might need change T88 to match with used printer model. need further test using another model if its compatible still by using T88
            mPrinter = new com.epson.epos2.printer.Printer(com.epson.epos2.printer.Printer.TM_T88, 0, mContext);
            epsonTarget = "TCP:"+target.split(" \\(")[0];

        } catch (Epos2Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * to format the receipt using epson's syntax
     */
    private boolean createReceiptData(String data, String extraData) {
        try {
            JSONObject obj = new JSONObject(data);
            JSONObject extra = new JSONObject(extraData);

            //logo
            String imgLogo = extra.getString("invLogo");
            String[] logoText = imgLogo.split(",");
            String base64Logo = logoText[1].split("\"")[0];
            byte[] decodedStringLogo = Base64.decode(base64Logo, Base64.DEFAULT);
            Bitmap decodedByteLogo = BitmapFactory.decodeByteArray(decodedStringLogo, 0, decodedStringLogo.length);

            Bitmap imageWithBG = Bitmap.createBitmap(decodedByteLogo.getWidth(), decodedByteLogo.getHeight(), decodedByteLogo.getConfig());  // Create another image the same size
            imageWithBG.eraseColor(Color.WHITE);  // set its background to white, or whatever color you want
            Canvas canvas = new Canvas(imageWithBG);  // create a canvas to draw on the new image
            canvas.drawBitmap(decodedByteLogo, 0f, 0f, null); // draw old image on the background
            decodedByteLogo.recycle();  // clear out old image

            float aspectRatio = imageWithBG.getWidth() / (float) imageWithBG.getHeight();
            int newHeight = 150;
            int newWidth = Math.round(newHeight * aspectRatio);
            Bitmap logo = Bitmap.createScaledBitmap(imageWithBG, newWidth, newHeight, false);
            imageWithBG.recycle();

            //invoice
            String imgInv = extra.getString("invBase64");
            String[] invText = imgInv.split(",");
            String base64InvLogo = invText[1].split("\"")[0];
            byte[] decodedString = Base64.decode(base64InvLogo, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            decodedByte = decodedByte.copy(Bitmap.Config.ARGB_8888, true);

            Bitmap bmpSrc = decodedByte.copy(Bitmap.Config.ARGB_8888, true);

            Canvas canvas2 = new Canvas(decodedByte);
            ColorMatrix ma = new ColorMatrix();
            ma.setSaturation(0);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(ma));
            canvas2.drawBitmap(bmpSrc, 0, 0, paint);

            float aspectRatio2 = bmpSrc.getWidth() / (float) bmpSrc.getHeight();
            int newWidth2 = 480;
            int newHeight2 = Math.round(newWidth2 / aspectRatio2);
            Bitmap invoice = Bitmap.createScaledBitmap(decodedByte, newWidth2, newHeight2, false);
            bmpSrc.recycle();
            decodedByte.recycle();

            String method = "";
            StringBuilder textData = new StringBuilder();
            final int barcodeWidth = 2;
            final int barcodeHeight = 100;

            if (mPrinter == null) {
                Toast.makeText(mContext, "Printer not connected", Toast.LENGTH_SHORT).show();
                return false;
            }

            try {

                method = "addTextAlign";
                mPrinter.addTextAlign(com.epson.epos2.printer.Printer.ALIGN_CENTER);
                mPrinter.addImage(logo, 0, 0,
                        logo.getWidth(),
                        logo.getHeight(),
                        com.epson.epos2.printer.Printer.COLOR_1,
                        com.epson.epos2.printer.Printer.MODE_MONO_HIGH_DENSITY,
                        com.epson.epos2.printer.Printer.HALFTONE_ERROR_DIFFUSION,
                        com.epson.epos2.printer.Printer.PARAM_DEFAULT,
                        com.epson.epos2.printer.Printer.COMPRESS_NONE);


                mPrinter.addFeedLine(1);
                String header = extra.getString("invHeader");
                textData.append(header.substring(2, header.length()-2));
                textData.append("\n");
                mPrinter.addText(textData.toString());
                textData.delete(0, textData.length());

                method = "addImage";
                mPrinter.addImage(invoice, 0, 0,
                        invoice.getWidth(),
                        invoice.getHeight(),
                        com.epson.epos2.printer.Printer.COLOR_1,
                        com.epson.epos2.printer.Printer.MODE_MONO_HIGH_DENSITY,
                        com.epson.epos2.printer.Printer.HALFTONE_ERROR_DIFFUSION,
                        com.epson.epos2.printer.Printer.PARAM_DEFAULT,
                        com.epson.epos2.printer.Printer.COMPRESS_NONE);

                method = "addFeedLine";
                mPrinter.addFeedLine(1);
                String invFooter = extra.getString("invFooter");
                if(invFooter.equals("[\"\"]"))
                    invFooter = "";
                textData.append(invFooter);
                textData.append("\n");
                mPrinter.addText(textData.toString());
                textData.delete(0, textData.length());

                method = "addCut";
                mPrinter.addCut(com.epson.epos2.printer.Printer.CUT_FEED);
            } catch (Exception e) {
                e.printStackTrace();
                mPrinter.clearCommandBuffer();
//            ShowMsg.showException(e, method, mContext);
                return false;
            }

            textData = null;
        }
        catch(JSONException e){
            e.printStackTrace();
        }

        return true;
    }

    private boolean createReceiptOrderData(String data, String extraData) {
        try {
            JSONObject obj = new JSONObject(data);
            JSONObject extra = new JSONObject(extraData);

            //invoice
            String imgInv = extra.getString("invBase64");
            String[] invText = imgInv.split(",");
            String base64InvLogo = invText[1].split("\"")[0];
            byte[] decodedString = Base64.decode(base64InvLogo, Base64.DEFAULT);
            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
            decodedByte = decodedByte.copy(Bitmap.Config.ARGB_8888, true);

            Bitmap bmpSrc = decodedByte.copy(Bitmap.Config.ARGB_8888, true);

            Canvas canvas2 = new Canvas(decodedByte);
            ColorMatrix ma = new ColorMatrix();
            ma.setSaturation(0);
            Paint paint = new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(ma));
            canvas2.drawBitmap(bmpSrc, 0, 0, paint);

            float aspectRatio2 = bmpSrc.getWidth() / (float) bmpSrc.getHeight();
            int newWidth2 = 480;
            int newHeight2 = Math.round(newWidth2 / aspectRatio2);
            Bitmap invoice = Bitmap.createScaledBitmap(decodedByte, newWidth2, newHeight2, false);
            bmpSrc.recycle();
            decodedByte.recycle();

            String method = "";
            StringBuilder textData = new StringBuilder();

            if (mPrinter == null) {
                Toast.makeText(mContext, "Printer not connected", Toast.LENGTH_SHORT).show();
                return false;
            }

            try {

                method = "addTextAlign";
                mPrinter.addTextAlign(com.epson.epos2.printer.Printer.ALIGN_CENTER);

                method = "addImage";
                mPrinter.addImage(invoice, 0, 0,
                        invoice.getWidth(),
                        invoice.getHeight(),
                        com.epson.epos2.printer.Printer.COLOR_1,
                        com.epson.epos2.printer.Printer.MODE_MONO_HIGH_DENSITY,
                        com.epson.epos2.printer.Printer.HALFTONE_ERROR_DIFFUSION,
                        com.epson.epos2.printer.Printer.PARAM_DEFAULT,
                        com.epson.epos2.printer.Printer.COMPRESS_NONE);

                method = "addFeedLine";
                mPrinter.addFeedLine(1);

                method = "addCut";
                mPrinter.addCut(com.epson.epos2.printer.Printer.CUT_FEED);
            } catch (Exception e) {
                e.printStackTrace();
                mPrinter.clearCommandBuffer();
//            ShowMsg.showException(e, method, mContext);
                return false;
            }

            textData = null;
        }
        catch(JSONException e){
            e.printStackTrace();
        }

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
            mPrinter.disconnect();
        } catch (Epos2Exception ex) {
            Log.e("errprinterstatus",
                    mPrinter.getStatus().getConnection()+"");
            Log.e("err status 1", ex.getErrorStatus()+"");
            ex.printStackTrace();
        }

        try {
            Log.e("epsonTarget", epsonTarget);
            mPrinter.connect(epsonTarget, com.epson.epos2.printer.Printer.PARAM_DEFAULT);
        }
        catch (Epos2Exception e) {
            Log.e("err status", e.getErrorStatus()+"");
            e.printStackTrace();
//            return false;
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

    private void disconnectPrinter() {
        if (mPrinter == null) {
            return;
        }

        while (true) {
            try {
                mPrinter.disconnect();
                break;
            } catch (final Exception e) {
                if (e instanceof Epos2Exception) {
                    //Note: If printer is processing such as printing and so on, the disconnect API returns ERR_PROCESSING.
                    if (((Epos2Exception) e).getErrorStatus() == Epos2Exception.ERR_PROCESSING) {
                        try {
                            Thread.sleep(DISCONNECT_INTERVAL);
                        } catch (Exception ex) {
                        }
                    }else{
                        e.printStackTrace();
                        Log.e("disconnect fail", ((Epos2Exception) e).getErrorStatus()+"");
                        break;
                    }
                }else{
                    e.printStackTrace();
                    break;
                }
            }
        }

        mPrinter.clearCommandBuffer();
    }

    @Override
    public void onPtrReceive(com.epson.epos2.printer.Printer printer, int i, PrinterStatusInfo status, String s) {
        ContextCompat.getMainExecutor(mContext).execute(()  -> {
            // This is where your UI code goes.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    disconnectPrinter();
                }
            }).start();

            dispPrinterWarnings(status);
        });
    }


    private void dispPrinterWarnings(PrinterStatusInfo status) {
        String warningsMsg = "";

        if (status == null) {
            return;
        }

        if (status.getPaper() == com.epson.epos2.printer.Printer.PAPER_NEAR_END) {
            warningsMsg += "Paper near end";
        }

        if (status.getBatteryLevel() == com.epson.epos2.printer.Printer.BATTERY_LEVEL_1) {
            warningsMsg += "Battery near end";
        }

        if (status.getPaperTakenSensor() == com.epson.epos2.printer.Printer.REMOVAL_DETECT_PAPER) {
            warningsMsg += "Check paper";
        }

        if (status.getPaperTakenSensor() == com.epson.epos2.printer.Printer.REMOVAL_DETECT_UNKNOWN) {
            warningsMsg += "Check printer";
        }

        if(!warningsMsg.isEmpty())
            Toast.makeText(mContext, warningsMsg, Toast.LENGTH_SHORT).show();
    }
}
