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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;

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
import java.util.regex.Pattern;

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

                printers.add(new Printer(0, BLUETOOTH_TYPE, device.getName()+" "+device.getAddress()+" ("+BT_PRINTER + ")", connection));
                WritableMap params = Arguments.createMap();
                params.putString("printerName",device.getName()+" "+device.getAddress()+" ("+BT_PRINTER + ")");
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
                printers.add(new Printer(1, USB_TYPE, device.getManufacturerName()+" "+device.getProductName()+" ("+USB_PRINTER + ")", connection));
                WritableMap params = Arguments.createMap();
                params.putString("printerName",device.getManufacturerName()+" "+device.getProductName()+" ("+USB_PRINTER + ")");
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
                        printers.add(new Printer(2, WIFI_TYPE, deviceInfo.getIpAddress()+" ("+WIFI_EPSON_PRINTER + ")", deviceInfo.getTarget()));
                        WritableMap params = Arguments.createMap();
                        params.putString("printerName",deviceInfo.getIpAddress()+" ("+WIFI_EPSON_PRINTER + ")");
                        sendEventToReactFromAndroid(mContext, "PrinterEvent",params);
                    }
                    else {
                        printers.add(new Printer(3, EPSON_TYPE, "Epson "+deviceInfo.getDeviceName()+" ("+EPSON_PRINTER + ")", deviceInfo.getTarget()));
                        WritableMap params = Arguments.createMap();
                        params.putString("printerName", "Epson "+deviceInfo.getDeviceName()+" ("+EPSON_PRINTER + ")");
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
//        String json = "{\n" +
//                "    \"bill_id\": 23666,\n" +
//                "    \"customer_name\": \"Guest\",\n" +
//                "    \"order_collection\": \"[{\\\"id\\\":94,\\\"name\\\":\\\"Milk Coffee\\\",\\\"price\\\":15000,\\\"quantity\\\":1,\\\"type\\\":\\\"recipe\\\",\\\"purchprice\\\":5250,\\\"includedtax\\\":0,\\\"options\\\":[{\\\"optionName\\\":\\\"Sugar\\\",\\\"name\\\":\\\"Less Sugar\\\",\\\"type\\\":\\\"option\\\",\\\"price\\\":0,\\\"purchPrice\\\":0}],\\\"productNotes\\\":\\\"\\\",\\\"original_price\\\":15000,\\\"original_purchprice\\\":5250}]\",\n" +
//                "    \"total\": 15000,\n" +
//                "    \"users_id\": 10,\n" +
//                "    \"states\": \"closed\",\n" +
//                "    \"payment_method\": \"Cash\",\n" +
//                "    \"split_payment\": null,\n" +
//                "    \"delivery\": \"direct\",\n" +
//                "    \"created_at\": \"2023-08-28T07:57:55.000000Z\",\n" +
//                "    \"updated_at\": \"2023-08-28T07:58:23.000000Z\",\n" +
//                "    \"deleted_at\": null,\n" +
//                "    \"outlet_id\": 9,\n" +
//                "    \"servicefee\": 5,\n" +
//                "    \"gratuity\": 1,\n" +
//                "    \"vat\": 11,\n" +
//                "    \"customer_id\": null,\n" +
//                "    \"bill_discount\": 0.67,\n" +
//                "    \"table_id\": null,\n" +
//                "    \"total_discount\": 100,\n" +
//                "    \"hash_bill\": \"fb4d7a85e19ad82a9d65536a7d5837a2\",\n" +
//                "    \"reward_points\": \"{\\\"initial\\\":0,\\\"redeem\\\":0,\\\"earn\\\":0}\",\n" +
//                "    \"total_reward\": 0,\n" +
//                "    \"reward_bill\": 0,\n" +
//                "    \"c_bill_id\": \"1359\",\n" +
//                "    \"rounding\": 469,\n" +
//                "    \"isQR\": 0,\n" +
//                "    \"notes\": null,\n" +
//                "    \"amount_paid\": 18000,\n" +
//                "    \"totaldiscount\": 100,\n" +
//                "    \"totalafterdiscount\": 14900,\n" +
//                "    \"cashier\": \"premium1staff1\",\n" +
//                "    \"totalgratuity\": 149,\n" +
//                "    \"totalservicefee\": 745,\n" +
//                "    \"totalbeforetax\": 15794,\n" +
//                "    \"totalvat\": 1737,\n" +
//                "    \"totalaftertax\": 17531,\n" +
//                "    \"rounding_setting\": 500,\n" +
//                "    \"totalafterrounding\": 18000,\n" +
//                "    \"div\": 1,\n" +
//                "    \"bill_date\": \"Mon August 28 2023 14:57:55\",\n" +
//                "    \"pos_bill_date\": \"Mon August 28 2023 14:57:55\",\n" +
//                "    \"pos_paid_bill_date\": \"Mon August 28 2023 14:58:23\",\n" +
//                "    \"rewardoption\": \"true\",\n" +
//                "    \"return\": 0\n" +
//                "}";
//        try {
//            Log.e("formatted", parseJsonFormatBillNonEpson(json, ""));
//        } catch (JSONException e) {
//            throw new RuntimeException(e);
//        }

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
//                    runPrintReceiptSequence(text);
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
                            // YOUR PRINT CODE HERE
                            try {
                                String img = "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";

                                String img2 = StringUtils.join( new String[] {
                                        "iVBORw0KGgoAAAANSUhEUgAAAgAAAAMWCAYAAACHgD7IAAAAAXNSR0IArs4c6QAAIABJREFUeF7s3WWQJMe1PvwUMzMzMzMzM1qWxZIFvsaww9f+5PDf1zLJ1rWFFpPFzMzMzMzMrDd+eePsW2r1TPfs7OzO7pyK6Jjd7qrMk09mnec5J7","Oyxvrmm2++KXkkAolAIpAIJAKJwJBCYKwUAEOqv7OxiUAikAgkAolARSAFQA6ERCARSAQSgURgCCKQAmAIdno2ORFIBBKBRCARSAGQYyARSAQSgUQgERiCCKQAGIKdnk1OBBKBRCARSARSAOQYSAQSgUQgEUgEhiACKQCGYKdnkxOBRCARSAQSgRQAOQYSgUQgEUgEEo","EhiMAoEwBff/118fniiy/q3wknnLCMM844tQt89/nnn5dxxx23fucz1lhjDcHuySYnAolAIpAIJAIDg8AoEwDvvvtueeWVV8qDDz5Y3nzzzbLBBhuUueeeu7by9ttvL7feemuZf/7563ezzTZbmWiiiQYGgSw1EUgEEoFEIBEYggh0JQBE6G+99VZB2h999FH58ssvK1","Sich8R+vjjj18mmGCCMsUUU5TJJ5+8fjf22GPX8955553yxhtvlMkmm6xMMskkZeKJJ66k//jjj5crr7yyPP/88+WAAw4oyy23XD3/7LPPLqeffnpZeeWVy7LLLlsWXXTRWmZPx8cff1w++OCD8vbbbxc7G88wwwy1HlmFdofz3nvvvWovW9gV2YeRPQbY++mnn5YPP/","ywYsIOGLKJbc3jq6++qtkR/eD8Tz75pPaFMmRLtAdOgXNkTfz+/vvvV5w/++yzHptIaMGueehv18JXfcZC9PdUU01V69P3owq/kd1fWV8ikAgkAmMKAl0JAKRx9dVXl9tuu6089thjlXwcnP54441XiXbaaacts8wyS1lmmWXKkksuWQkYMThuuummcskll1QiX2ihhW","pUj1hGlAB4+umnaybhmmuuqWS42WablQUXXLDa0+647rrryt13313Jbp555imLLbbYd8h2ZHUwQn3xxRfLww8/XK644oqKG9HDprnmmus7ZEy83HHHHbW9L7zwQiVmogARzzTTTGWppZYqiyyySM2eEAUOdWjvkUceWV599dWKUbtjzz33LFtuueW3ftLf9913X63vue","eeK8QWG6ebbroq0NQ3/fTTjzL8RlY/ZT2JQCKQCIxpCHQlAER+5557brnlllvKa6+9VgkAuUYGAKEgIefNOeeclXyXWGKJSkgO111++eVVAPhtjjnmqCJiRAmARx55pJLiRRddVCP7VVZZpay++upltdVWG2Zjs+MuuOCCcuONN5ZZZ521LLzwwmWFFVYok0466UjvWx","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","uss85aZp555pFoQVaVCCQCiUAikAj8HwJ9EgAiVxHsJ598Uj788MPy2Weflc8//7x89dVXtbCxxx67jDfeeGX88ccvk08+eZlooonKOOOMUyPdwXSw94svvigffPBBtW2qqaaqdg708dZbb5VjjjmmvPLKK2XZZZctSy65ZFl44YWHq1ptgP/HH39cCAvtiX7QBxNOOG","GZbLLJyiSTTPKtTINzvvzyy/L+++/X6/SfPnXotwkmmKBMMcUU9fqe+k5dn376aR0Dypt22mnrdQ6Y+vhdPQ6/GQvs8bc5HlzPBvZoi39HhoQ9zjeWlNFTH8Eh7DEG2eOv+tkY7VSXsgOfnsao61wTH9cEPvB0nTLU4YCfstmv7doAIwebneuaSSedtP4/rnOOc9nono","q2u27cccetbQ7MlNHtfcRen+gL+ChrhhlmqLZ0c8AApsQq+6aeeurvtLuncrTr1VdfrdcFdq3nTjnllGXGGWesWDj/jTfeGDaeerpGGew3JvSxMZpHIpAIDD8CfRIAnByn8OSTT5Z77723PPfcc5XMOL5w9ByFqHbFFVcs8847byWTkUGufYGAY+dwbr/99kp6a6+9dn","VuA32MSAHAub700kvlkUceKffff39tD4cfBCjDsNxyy5UllliikkmQjra/++675ZZbbikPPPBAddT6lNNFEK5baaWVytxzz10xieua2Lz++uu17++7777qtLfYYosyxxxzVMK9++67yx133FF/f+eddyppGQ/zzDNPWWGFFcr888//LUHC5tdee63a8+ijj9Z/IwT1Io","j55puv2jPLLLP02EdweOaZZ+qYRA6bb755mXjiiWs72fPQQw+Vl19++VuChb3KNUa1szlG2Q0b1z344INVaGnHTDPNVBZddNGyyiqrVEJUlwNxI0p9oT516Wv3C9LXjuWXX74stdRSw8SM65zj/mH3Y489VuCqLofytdl1bJxuuulqP3ZzECQwvPPOO+sYf/HFF2u2aZ","dddqnldHPA4Nlnny033HBDbdMmm2xS+88YCbHXrhx1u/bYY4+t9fdE5mussUbZc889a1lwOOOMMyoOhGAI2XblI354GHPGaB6JQCIw/Ah0LQA49zfffLM6xccff7yKgLi5m3PaEe2sueaaZaGFFvqOcx1+U0fclZzu008/XW6++eZq33bbbVcd7kAfI0oAwFsbbrvttv","L888/XfuHw9ZHfIspfZpllaqZhttlmKyIuh35D3PoQ4URk6Tpl+P8CCyxQiY54aBdlIWqEjVicDz9ZFLbcddddlTwj0kWcykboROFiiy1WBQGCdiBY57MHYRNkDtcgVgTKHtexp10UfM8991TRQQggqG233bYSKVLVXoQcmaooF5kSI9q59NJLV3v8xgZi4rrrrqtiBK","ZRJ3JDQOxZZJFF6l8HwoOJunzUFX3hr/8jK/eDuggJx1NPPVXb/8ILL1QB18zG+DfMiABtX3nllWs2p7eDfdrNbuObTWEXIfWLX/yiCoFuyoCB++PWW2+tOO68885VKM8+++zDhE+7cthtbB588MFVmC644ILD+rp5PnG6/fbb1/6G+cUXX1wFFEEZGanm+e4d/UjAwH","GnnXYahv9A37dZfiIwpiLQlQDgGEX5HPVxxx1Xo0aOn4rn0JAnZ+UcTlg0hHhEWW7wblOXIwtkIkZbODbR2egmAJAK20877bSKLxLjmPWDVLJI6qKLLqrOEkmtt956NWpyXHLJJeXUU0+tRCYq14fO08eiPUTKISO4XXfdtRJ762Edg4gNmSAWxI4EL7300kpmSGijjT","Ya5qAJlSuuuKKOB1MexCHcHWy5/PLLqz1+Yw+iU8Y111xT+4k9q666arWnXUYCeVx11VVV6CAH9sBHucoK0oaPcYvY2MRm54tEYQBXhClydS1MRZquI6qInhBOm222Wdl6661rG0TK2o68CBbCAjbqIgi0g9jSV+oizBywhjksiCJkT4hoIxt8Hn744Uqi//Vf/1WmmW","aaXu8lAu7tt9+ubTv77LNr/SJsQoNN3QgAGCBbdZ911llVmLh/t9xyy5r56CQAYEAI/vOf/6xTKPvtt1/bdS76Ba7Khi2fYez2FP0b08aQjKJxtP7669e/eSQCicDwI9CVAOBYRI2cAucvyuIMpIsjmnMjO08Kz43MqXGGDpEApySdydEiYCLCudKiCMxcJ+XPEYheOE","4OhEPixCKNrO5WUuKAOTnOyvWiEBGe6JXjYyfnKdUtOrnpppuq8+XoOCJkx7GwF3kgRgdnKupQfkSRymWrc5Cqf0dKNNYWIBH2iMzDFvar69prr612IAERLeeOHETTyAVubOhtzhtObEcO7IEJ+5EHvERS6nEOEthxxx3L4osvXtvEqVuHoP+kdEWXsgMEALIVwesrjv","7AAw+s5BmHerUH2R199NFl4403roSN7Fz773//u5KYurQtFjhqlygdKWvXD37wg4qdMaMc5bFHVOha/WYsucaYY490PXuaAiDwJoQIm0033bS2ie36zjhSX4iAWJPCHoRy5ZVXlumnn7789Kc/rVG58i677LI61olZgmTdddetuGo7YpNSJ66Iqm222abiTqzobzarD2","YxrWCc66err766ZhZ+8pOfVDGjHX4jmIwh58faArgYc+4bQsuY+PnPf15t7G0On83seOKJJ+onylO3+66TANB+Yuv666+v49GhHNM5hFCrAICFNsHQGJhrrrnq/ee7k08+uV5/wAEH1DHResgQNbM9sIsnZJrnxhoRbTjppJPKaqutVrMh7pt24nT4XWFemQgMPQS6Eg","Aci4jCDY+0V1999erEObHeovu4ef/zn/+UI444onzve9+rUSFy4qgQ1FZbbVWJhNNUD+dszpHzDzLnuKVQkYOMg6izuUBNZCaS4jSJAALE766TiQiyRbycsUiCCEBmHConzgmzwTwpEuGQiJCIwpCJ6xEux6pc57ErIjPRj6hS+bDi4IMUEB4HrkwkEYsA4RFRJ/JByP","vuu28VI+Eg+zosIxUPQ+JC1IlcHQgF6Zon50w57RBq+gSRI1Ok86Mf/ehbAgBeCOLCCy8sRx55ZCVkxGAccNB/+tOfKglLF4vuIs3vGsJLvYQUItJO2BsXom32wJM92k3IBP7sYb8ouCkA9LMxcsopp9QyDjrooLLWWmtVAdEuUxA4EgeITZSqL37961/X7IE6iSNjhD","C1SNPf5mI/UwN/+9vfqpjVVlFob0RkTMTUj2vVJQPS06K+WMDHRoIJzsbbj3/840qkvS18s56CuIaJ8o1p4++EE06o7ewkANzbxg6RyI4NN9ywTgHIYLQTAASfgAAZG88EgvGv384999wK9/e///0qUJXnnozFjfq4m/FNXMHPGNA3e+21V7VF27pd0NjX+yfPTwSGCg","IdBUCsJuYszVeK7ESoyKNTaj8EAAf997//vRIxwuMwkQ6CRuacr3o43nPOOac6PMSKRDgMTiXmS6VeRZ1+C2co4kWi4VT85TRMR4he1CN9zIFykshfVOkckRFBwybXcejKFQGJhjltDk7Ej9Bcr0xCg3M130yUcG7sIEbYSszASnTE+UYmQVSnHBFk4EFc+F4aW0p699","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","9/PayVY401VhlnnHHK+OOPXyaYYIJKuP7dn+PTTz8tPupV9iSTTFL/djrYdcstt5Tf/e53ZZlllikHHXRQmXzyycvEE0/c6dK2v3/xxRfVjs8++6x+lM+msccee1hb2wkM1zkfXv791Vdf1fJdBxvXsEmb4NfucK26/U7ARBu+/PLL8tFHH9Xf/NvhnIkmmqh+9EETK/","a2s6fZb65Tvu/a2aMMdep7v6vD+cr13SeffFL/hj2d2qk8+ESZcR27lR1tadrinOgPbYep/gh84Gqc+BvXqYddMZ6U0ewLuKovxhe7uz2UpWz3hTLZrF99ujkC0w8++KDay+7JJpuso1iN/tSm999/f1h7mnVqh7YpT9vicK3r4j7WBt/FWNCncf/2BYtu2pvnJAKJwO","BDoCsBwFnffPPN5Z577ilPP/10ddyOIH4kK+JeYIEFyvzzz1/mmGOOfrX08ccfLw8++GB1+FNPPXVZdtlly1RTTdWxzBEpADjGV155pbDlscceK88991zhrNXBSc4zzzxlwQUXLAsvvHCZccYZv+VkXffMM8+Uu+++u7z44ovl3XffrVhxsLPPPnu9TpvgNu6447Ztl2","vVyxHDlqBBNG+//Xa57bbbyiOPPFLeeOON6sDZs9hii1Vb5ptvvjLFFFMMKxOG7HniiSdq/7366quVOIIg2LPoootWe1rFQxSC6O64447y/PPPV3vmnXfeas/LL79cv9NX/r711lv1EmSm3IUWWqiWO+WUUw5rJ3sRz1NPPVVuv/32iuvrr79er9PHc845Z1lyySXLEk","ssUesKMtdu7VCXtqjLuHTOzDPPXOaee+5al7EXmOor4/XJJ58sjz76aL3+vffeq7/DTL8Zr8stt1wdZ92SN1th/8ILL5Qbbrihlrn44ovX9roHOh0w0JfE6tVXX13xmm222cp6661XZphhhl4vdx3cH3rooXLVVVeVN9988zvnG1fKWWuttcpKK61Uf4cF3OFw11131f","7SBuODeDFmYG4swHPSSSft1Iz8PRFIBEZzBLoSACK8c889t9x0003V6XFY00477TDnzAlzniKOFVZYoTpUDgShDM/BySMrxKOeVVddtUwzzTQdixpRAoCTRS4PP/xwFT7IB2lylpH90F6Etfbaa1fi1XZkxcneeOON5d577y2vvfbasEwG4yPCRo6rrLJKJbt2Dh9BnH","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","0QSAHQCaH8PRFIBBKBRCARGAMRSAEwBnZqNikRSAQSgUQgEeiEQAqATgjl74lAIpAIJAKJwBiIQAqAMbBTs0mJQCKQCCQCiUAnBFIAdEIof08EEoFEIBFIBMZABMYIAfDcc8+V559/vnz99ddliimmKPPPP3+ZeOKJx8DuyiYlAiMegQ8++KC8+eab5bXXXitvv/12GW","usscrUU09d5p577jLppJOWcccdt95fr776ann//ffr7+OPP369z2adddYRb1CWmAgkAiMFgT4LgG+++aZ88cUX5fPPPy9ffvll+eqrr4rvHBzD2GOPXcYZZ5wywQQTlPHGG6/+f6CPG2+8sfiwZ4455igbb7xxmWaaaTpWy27t+Pjjj6tDm2iiiWobfPIYdQgYU/pS3z","iIOWNqsB8EKLs//fTT+jfuAfdBf8aUcRpj1b034YQT1nurv4cy2YzcH3jggfL000+Xl19+ud6zyH+NNdaoQoDtt9xyS3nkkUeqQNAXk0wySdlggw3K0ksvXX/XZ/or/II+c08pqz9t728b8/pEIBHoGYE+CQA3+WeffVZE3A899FB56aWXyhtvvFE++eSTWoMbfsoppy","wzzDBDWXLJJcucc85ZJptssgF33sMjAMKhPvbYY+WSSy4pCy64YFlzzTWrCNCOPEYdAm+99VYdW0hJP62//vpl+umnH3UGdVnzO++8U6NoZIlIF1lkkbLAAguU+eabr0bRw3uESH344YfLU089VVZZZZVK0P09lPvee+9Ve88999x6v84+++zV1plmmqnary8Ig3vvvb","fIFGjP5JNPXondPT7PPPNUM15//fXiXrr//vtr3+mzxRZbrGbk+tP2/rYxr08EEoERIACQ/7vvvludwaOPPloFwEcffTQs2lGFGx2Bir7XXnvt6kA4i4F2AMMrAIiZu+++u5x44ollmWWWKVtvvXWNbERueYw6BKSan3322XLTTTdVAbDjjjsO6lSzceTeYPPjjz9ebr","755kqIK6+8cllxxRXL8ssv36+IHfHC5NZbby1EwHbbbVcj7/4e7l82X3vtteXss88uG264YRXBInz37YwzzliJXz8QNr5zX8sK6Be/myIQBPALbCMmlLnvvvuWtdZaqwq3EZGt6G9b8/pEIBH4LgJdZQDc7KJ8KcD//Oc/VeH7btllly0LL7xwjfpFBM7hDDgWTk9EgU","wHehpgeAWA9CfHxQHOO++8ZaWVVqrOanRIN4/Jg9k8szlpQtM40y9IZzAe7EP2d911V7nvvvtq1kJkTUgiafdIfwUA8ie4fV555ZURJgCk8wlgdovct9pqq7LeeuvVlL17wL1w+eWXl4svvrhmBET/BM1UU001rCv001VXXVUzE4TKM888U//uueeeVUykABiMozZtSg","T+D4GuBIB5R86Yk3OziwREzBYBmXMXBXAaHJ80qDn12WabrQoDjkSakTDgvDgdc6Qcp984CM5l5plnrtMFDvURE5yJazgU37nG3KJypR6nnXbamnEQocQaAL8RJeZgpS9dw5FxWrPMMktNncacrKwGh3366afXdOZmm21W0/+RsWDDhx9+WKc8pHS1y+EcNitPtoOzjz","So+dQoI7Im2sBu58Is6lWP75TTdKqtgxOmyg3MRJzs0j64EFjTTTddnXqBe/QHgUOMcfLOW3TRRWv/ED2+N5csTasP2Oc7v4vgOHbt10cwtthLm9kbUyTO1Z/6lm3GBTL00UbiL9qnjfqTeBQt6x/t1+8+bA8c1f3iiy+We+65p5a7ySab1N8dvlOG/heBSjE3BSZMHn","zwwdrHMNE29qtTuWxVj9/1qbHJVrYbT1Lg2sdW57quFQNlxhjRDn1zzTXX1AVyzjVejPGFFlqoLLXUUt8RADFfrh2wNr6b02jsNU61WT/LKhjfMHEuYUFcsxMOiy++eO1fvymP3WyJ9Tnay2Zlaqd+Ua7yZCuk7v0bYRNb7kPtM36uvPLKKgLc50SyqTJjAbbGocwAAW","3s6EdZCoHCHnvsUdcQpABIqkkEBi8CXQkAzum8886rN7cb3o291157VWfXzQKfJ598sqYS77jjjuocOFXOhRNeYoklapS02mqrVeerPM4JiVx66aXltttuqw4f+bqGk+aIkDXyQjDSjhwkAeKccMLq8n8O1TWrrrpq2XTTTYc5OOdzXn/4wx/KuuuuWw488MBKhBwr4k","EYnPnVV19d7fB/5XGQyy23XE3xIlWOj90EEofJySJX5YuM7rzzzuoIEcK2225bvz/yyCOrXcSKtouuejoQwBVXXFFJk23wISqkXIOk2YFspF05aA4cKTjvb3/7WyUHTpk9IjpRJSx33333eh2yQQLIgMgTbWovshB9s1GbYa592ssuGCNluCAJgkpfIwfnaZc5ax9tQD","hsQJDKFh0jHWUTCQ6YK5cdjv3337/MNddc9d9///vfyw033FC22GKLem0IusAO+R9zzDGV5Ik6bYO9doVthAPbjBt1sZVw0j9S3H5DeqJiYinEhP5eYYUVqq36LtaROEfblAF/8+nwYxtybs0AaDvhYcy6zvgmpGJs6RcLWd0XfjN+rFOJ+wZu2sdO98Hee+9dx8QLL7","xQLrvssmq3f4fQ1n73GSEFR2JTuUT9ddddV0UDMYfc9Zd2uA+Mn8iuwSw+cIIt3Nitre5DbT/++OPrdEIKgMHr9NOyRCAQ6CgAODnkgbA4FguaOEI3e7epfcRCBHBgCAuJITP/RiLKN//OSYnoOBRiISJLwgChcpCxYp9jRxgcIVJB5Bwxm6w94ET9pg5RIULhCBERR8","fJ9SQACAjXsQFZcm7KE1mzgbPlQDlxTpUtMg9SqQQAZxoEyElrE6erfnUTUSeffHJ1shwuR9/boq4QANrH4eoD0bj2cdKiL79x+MgG6ahH34lyDz744Eq6iJYdxAFS0y6YI3i4IT0ihp3O094gK4RC+ASJaC+7tNl17AjcXadP1Y1k2KmvYKEM5AkfOBkbItUddtih2u","I3dQ2EAED4bEWCMNRGQoat8PNhJzHEVnY3swfsIiTZKgI2Jtnueu3QbiR55plnVlx6EgDOJaaVpz+Nb30Ri2yNcX0ouocT8SLDQMxFHyvbfUR06WtjmWAjFtxn7Nan+iUEl3411ogY38HYGGeHa4g7glT7jRFZBML39ttvr6LSBz6wIwKMIfeDfoQFIXzUUUeVs846Kw","VAckwiMBog0FEAcEpucCSCxEWwnIhUdrcHJ4f0RC6Rwo/0uAV4HPP2229fCYqTuf7668uFF15YyYITtKIYAXM2IlcOkqONVKUpAFE6p8sRifJFKJx1kOMZZ5xR/6080SzS7kkAsE09nK7rkJ7IkPNDqn47//zz6+9bbrllzSxwyCJyEd0TTzxRRQJC5FTZgkzYxjETD6","Jb/ydEYKk9nTIAUvnSzewhZLQPJpy3DI32c8JEwEYbbVQzIeqS4RAZh9gQwSIcJK4/4ElsidyUYS5YH+sLIkeUfOqpp1YhJfLWHu0lAAgGhIdspJDVjRyQh0yA6/S930W1sEJaiIlAMP0ig2DRmDaxi80DIQCMEbaavlp99dVrG40rthJ655xzTu1f/cxWfcfWmIMn2o","iGffbZp6bg4dd66I/TTjuttqknAaBtxhWRqTzj21gJMlUfsmZbPIMvEhepG8PtFgHGdI9zXKMN7jeCgTjWrwSEPnK9PtV/xhQB4B7XdyFm4WBcEddEgLb4aDdMiBZjp3XB7OGHH14xzgxAt94xz0sERh0CHQUAJ8WB//Wvf62OWbpRpCYC7faIfQNijwAOioORBUAQCJ","/zN7eJPKQlEazIWbRhdXLMASNtBB3z7CI4zpGT4/yc973vfa86KQ4qyBzhIk8RFYdLBPQkABCCSEr0ai44iCucnTKkZJG9aBJRsF9U5TsRG7vYITUvmkTG0X5OmSjyfyKAOOjt0cPIALCHo95ll11qFoaYcMBXRImMnatOUzTKDAHA4Zvm4OQRuOhNP7ALbtobWYTNN9","+89oP26iMiAFETNtqLOLU3BAAyMx5++MMfVtFAmLkODkSFiBfWVvMTAOxGWHA87rjjan8THSJT/YaoBkIAGAPIiR0EJ/KNNhJzRCeCRZ7OQXiRrYDREUccUe0m+oipmLJo3gfdCAAZLiLBOCB4jG8RvwNucNF3sVZF33USAO4nY12Zxl4svoU7LI0N94jMHXJ2HgFGfB","u3RAfR6L6I9Q3GFWHkwz7jnFBhs3EbY7rZ/hQA3XrFPC8RGPUIdBQAHCLnQAD4+6Mf/ag6gr6syhYNiyZEkMiEY+Eog7wRScwDExciaZEH4cEJqi8WHnHaCKa5wQjnSERIgYqod95552GCAcTqjcVOCFGGQETfkwAQDZtLRWBsiGxCs7tMh3Dk7OIcOXEkQQCIqIgPjl","Z6tpt1Er0NhRAAIWAIC1F882CraI5wIpp+/vOfVyfNycsAaBNcRHjx7HZcry3a61yYEGIhuJzjO2lgZWivvtLeiCBlHwgDdcYTIa5DLBdddFEVIUhN6ty1zcN8PWJeZ511aptkQ4yzgRAAhIb5eSvUiajWMaTvjFVZFLY2d7kjYg477LCaLSGkpMpjXUJfBQBCN15j7t","34kc0R8SNXY7y5vsZ5nQSAe4pt1s4Qie5b95i+i8f0jA9idr/99qsm+165+pHoIvzcF80DXjIjxk18CNqejhQAo96ppwWJQLcIdBQAnAqH/Je//KWS4Y9//OMaYbZLf/ZUKYcneuWAYs6fw0KMhAFHbFcx0SEngyyQhuhRKtXvHKTIkxgQmYlSYg1CLFQS1UhNcmScaR","yibQSGVES5Fk553KknAYAIRbWiYtMeREjrs8xhPyxE40QFgrYGwJwwJ25dQzuS6LZz4rwQAAQOzESgETHGOfpImtdaDWna3/72t1WEhABgk0WOBEmreJPiFRnrI+eLHpuPQsZiNxkZ7UXWpiFkBNRJPGjnQQcdVLMdcSAuc+7xVADcW59ft2hMal7kKTUv82CcDYQAIA","LZ+v3vf78SfPOQQREhG3sEgP5sZrmMR9gSCGyFgzHZenSTAQiiNlZkHeIpDu0ngI3xZh90IwDYpe9Mh8GOCNUWGQVjNT4yHwcccEAKgL7ehHl+IjAGItBRAHAciOdPf/pTnTcU1YqI26U/W/GJfQEQKZI2zx2P7iFwZIaYObjIACAI5COCQUjqVL//c2iif9GpVLYITZ","QbjwGylQBATv0RAJy9lDe7iASOvnXPc6QYjjseL+R4OXXtFskRIlLa/T2aAkCd5uFbBYAILgQAMm4VAPD7yU9+UrMDrREc8iN4kIh+EYW3igTEBmuL37RJFiEWAWqzOq3WbycAYjFnLJhs4tEUAPpeZN1fAfDvf/+7ih/lNZ8CkO5mqwyKefDmQQSZBkCYiJ+tzXUZxo","QFboRUfwVAbBdsegmGkRmL1L/FhLHHBsxlHzplADwZof8JatewnWg1Tglg95FMD2GTAqC/d2RenwiMGQh0FAAciLS9DIA0s3lCqVoRUKeDY5PO51hFV6Iu0aMIK/bdtwbA7wiNsOC0my/yiefwLWIzjynK4dg48HDu8RhgT+8CGJ4MgJXMriM4pHw55E5HPAZINBAgSI","Qg6e8RAkAk7YgnJprlyj5YdHfCCSdUcv71r39dMY4MAFHys5/9rIqZeN4+rifCtFfUScxYI9FN5oJdSKUpAGIvB2VHBoCAM46kn/XZQAoA9ojUkahx2hQAhI7pBlMhFrO2CgBjlIg0Ptk6UAKgWS9h1VyQZ9pEn8WmPBYD6vcQAO4HUXxkUmI/f+sTTCvIjskkWOwXfa","EftF0q31hOAdDfOzKvTwTGDAQ6CgDN5HQQi3S8yFbKWzq309y2NCSnJFUsRb3NNtvUSB8xRYrZHLA0sR3GkGyrAEDqUqZEiOgG2SCepmDwKFNvLwPqqwCINQCmC8ynIguLpzodAy0AOHJEvuuuu9YMSPOwHkH9sLRAzVSNSDAWAfYmAGINAKJ2jQVwUvGdjlEhAA499N","Da1yFEZSua0zOmmewVYIrKSv/BLgAQuPHtHjPW9KG1CKZqTAe4H/SdLFfsskdIhwCIRYPW6BByxIGxIUtjIWEsAiQA7KtB3KUA6DSy8/dEYGgg0JUAkJoUHSFac+KckwhJmti8cKxWjrliTkmUKS0t4hLhSJ2K2gkAwoHT4/BOOumkKhIQLBEQji12qovV0PHUgNXTnJ","lIh3O3biCmGEZUBoCtiER7fcy5WxyFVETJ7IcJ5xrrA9hJIBEonTIA8Yx8vFUNhr29vjgyALE5jxS2VfixIY/6YMhmmJtDOFYXvwAAIABJREFUtsit+RRAbwKAsEA8sfcCwSMbEzs5uhWsr7DKXHuV6zMqBMDRRx9dNwIS5cYnnscnEKXyZQBE79Z5DEYBoL+MAf3P9l","jPAl9t87hhbEykn4197dL/hLS+RfKmg2JXQY/p6j+PKHqaIhZxeqKFeHC9PpaVSgEwNJx7tjIR6IRAVwKAk0EsHMgpp5xSHZK55Oa7ADiz2H43nrdHOqI1jgvJ7LbbbjU1yXFFytqiLNMEVpUjcwJABB6P9EmlIzo2EA2mDDi6cP5sGNEZAGnZ2Inw2GOPraLFR0RmDp","z9omVTHMgbURJDkQ7vJABkRC644IIqJizYk83g8Hs6QgCI8GQlzOOahzZf7kB81iyYAxb9I4DAuZsMAJKAuT0Z9JfIOTYUih3vEA8REJsaae+oEADaiSRjpz9Y+DfMCTC/efLANEhMOcROgINlCsC4MQZgyzbjm7Ayvj2pQACYYosx7n4ztrTNveRRXPdKvG6bICUATI","UR2YS0qR6Y6DcLPN0j6pWFSwHQyS3m74nA0ECgKwEQLwNC2pyQ1D7SQAbmWiOCiSkBDtl2waYLEDnnIwKJfcSJhdjvX0YBmYo4Yw2AVCdnziHGnvG6AzEjNH85QARozppjH5FTALEToDl1EX3st6+9IrZ4/7l2x45thEq3AsB5plQQQLxgpd2K8hiCIQCQBmFCfInwRO","GBI0fPLhGjZ7WlgPVbNwIgyogsD3yVHa9yjfYSOtprQaT2jgoBQITqF3Pl+ime1Y+Fo+bPrRchqGJjm8EmANhIzCFkoioWxMLddJU+IziJ29h8KrJpxrlpD+sUYidJotkTM+4z9yNxpn8I1djHwb1LIBJMFms68jHAoeHks5WJQE8IdCUA4uJ47WlEI1L7HBOiRjZIg7","MVfZmLFMVwQB6/snEO8pL6jJfAIL/YD8BjcxaeibKtpufo1KOO2O8e+SJ9QkGEy8lzciIfIkBdvjPP2VzFbqpBetwiRoJEtsGUgwhJPeZPlSd9ipSbL7vRPlEZAePfshsIkbhRl7loDtj/RWdIVLn+79HG1qcHYMkR2wAnBIA1FUi9UwYgXqLjPGsiEDDs2YOYEYboHW","nENrBs1j4YekwPYfY03WD1PWK1KBOJEhUwjZc2ERbaq3+1j3iLNhMcnq9vLgJEOh7xQ3QOKfnWpxdiIyhRq341JhCTvoK7tik3nqYgPI0JUb669a1IWBRNRPnrHP0Pe+MJGbIVQSpTlOxJiubRnEaK6YPmXghw1Gf6n63GYbvtm43n2FFRdgdmIvnmOgW4mLIhcrVB38","hwGQ8WjSqbgDautD+mv/SLjFm8QEhbpfQ9mQOvuC/1m0N52m5s60fP9Bsf8HQQ3u4b2LhGxsTvzcNjij5wjM2AetsHwJoe+0KYpiBGZczydcBJQInA4ESgTwIgdm/jdJGFhUucF+fiiJ3tkEC8dCTS6aIa50Y0jYSajkQ6PKIXRKd89aiDc4xXlEa6nZOWafA9wiAuOE","rfiY6aW5TGPumIQXkI0qNeynUdJ6w8kVVzfwHXxT4InGVkJLQ1XsiCZJCh/7ObI9Vm/2dH64p714ryOF3EKsqONHA3AkB7YypCe2DvOwKKs0dasPUdO2COzLXVwr7YPrldXc6Nd8/D3v9DoCET2MdOf9qn/mizviNims4eWSJOwjG22G1966EpDX2NKGJNSbx/ILIayl","W+w/iJN9mpW9nGXYgAf51DxMFemYRjvGJYmYi59ekM/WssxMuj4kmVwAkWMjDwZissmmInztNOAiWeIHGe8dF8b0ZsruUcOMdb+4wH7XSNfnRdCADlwtJ9pP3RRv0BH4Iw7st4s2CsMTC2tYvw8O8Qm3B278TbCJXVFD3aJNvnMUT9Bkt/eyN0GKmHSDSuYd/tO0MGp4","tMqxKBMReBPgmAMReGwd2ymAKIaFfU181jmIO7VWldIpAIJAKJwKhEIAXAqES/y7pTAHQJVJ6WCCQCiUAi0DUCKQC6hmrUndgUAKYoLOTKDMCo64+sORFIBBKBMQGBFACjQS+mABgNOilNTAQSgURgNEMgBcBo0GEpAEaDTkoTE4FEIBEYzRBIATAadJjHvjxKaSW7KQ","CPiPX22OBo0KQ0MRFIBBKBRGAUI5ACYBR3QDfVx7bJ8Qilx8PaPV7YTVl5TiKQCCQCiUAiAIEUADkOEoFEIBFIBBKBIYhACoAh2OnZ5EQgEUgEEoFEIAVAjoFEIBFIBBKBRGAIIpACYAh2ejY5EUgEEoFEIBFIAZBjIBFIBBKBRCARGIIIpAAYgp2eTU4EEoFEIBFIBF","IA5BhIBBKBRCARSASGIAIpAIZgp2eTE4FEIBFIBBKBFAA5BhKBRCARSAQSgSGIQAqAIdjp2eREIBFIBBKBRCAFQI6BRCARSAQSgURgCCKQAmAIdno2ORFIBBKBRCARSAGQYyARSAQSgUQgERiCCKQAGIKdnk1OBBKBRCARSARSAOQYSAQSgUQgEUgEhiACKQCGYKdnkx","OBRCARSAQSgRQAOQYSgUQgEUgEEoEhiEAKgCHY6dnkRCARSAQSgUQgBUCOgUQgEUgEEoFEYAgikAJgCHZ6NjkRSAQSgUQgEeizAPjmm2/K119/Xb766qv618d3jrHGGqt+xh577DLOOOPUj/+PrINNbPEJG0ZW3aNbPZ9//nntQ8e4445bxhtvvLZNCDy/+OKLiuv444","9fsc3j/xBody/AKcb9qLwf+tNHxoY+dw/3NDb6U35emwgkAqMegT4JAE4BcTz//PPlscceK6+88kp58803yyeffFJbghymmGKKMt1005XFF1+8zD777GWSSSapTmSgD86KXW+99Vb58ssvy0wzzVTmmmuuga52tCwfPpdeeml5+OGHy8QTT1wWWWSRstpqq1Uh0Hp8/P","HHFdPrrruufPbZZ2XTTTctM8www2jZ7hFtNBxfeumlOu6eeeaZitP7779fx59jwgknHHY/LLzwwmW++earZDoy7of+tvXpp58u11xzTVlooYXKyiuv3N/i8vpEIBEYhAh0LQCQ/wcffFCdHeK4//77y3vvvVe/Q74RSU466aRl+umnL+uss04lFoKgHbGMaCyIkNtuu6","3aJ0JdcMEFy7LLLjuiqxkjykPkhxxySLnyyiurQFt99dXLTjvtVKaccsoy0UQTfauN7777bnnhhRfK0UcfXYiBX/7yl2XeeecdI3DoTyMQPfH70EMPlaeeeqq8/PLL9X6AUWRWQhBPM800FePll1++TDDBBINaAMheEDDupSOPPLKsu+66Zdddd+0PVHltIpAIDFIEuh","IAnAKCffzxx8tZZ51VXnzxxeokllhiiTL//POXySefvKY8EYsoyLkrrLBCjcBFQSMjZfzRRx+Viy66qDz77LM1A7HYYoulAOhh0IUAuOSSS2oKe8kllyybbLJJjfZkbZpHCoD2IN51113l5ptvrmJYVkyEP+uss1bxK8J3z3z66afFuCSSYWxMxjTZIPUHVbwQNzfddF","M56qijasZn7733Hqzmpl2JQCLQDwS6EgDI/oknniic3mWXXVajRil+Tg/Ji/qRPGJ5++23q9ObY445avTvWulE4mGppZaq5zfXBXCUzr/77rsrGUXWQKpUZkFUJQJV7ocfflib6nqRKqJXD4flnCuuuKJmAKaaaqpKZOqaeuqp63n+L92tvldffbV+wlZOj1DRjtlmm6","2er/xI1bIBERI3rneea994441qs0hP9DzjjDPWqQcCyfRIpIOVgxj8rmx2xBHtV57r1EVAaT+clecabVIOW52HVNTret+9/vrrFUe2uWbuuefuMfMSAkCKd9ppp60YsX+99dYrK664Yq0nRFtvAkD/qBP2sPHvSH/DT+SrfyabbLIa+Tpg8uijj9Y+ZKd2i6Rdqx3a5P","uZZ565lqXPpdn9rj6YwGLOOeesNsMpxhMs4CIa1zfK9p0MFJEKf+PANdqHuF977bU6naXMsNVY6OlA6vpI9gR+xjjidz/MMsssFc8YN/qR3dqsPdrlgCmhauyoy2/a0jyMH21gI9uN5ZiL16bABXYxLtXLHlM02gnLECNsgKPz1a9uh3P0ExvYAmPZvVtuuaW2j3BZa6","216rkwIhL9dV0eiUAiMHoj0JUA4MguvPDCcuutt1bykc7ca6+9qmNtF93HIqgQBGeccUY57rjjyn//93+Xrbfe+ltREEekzN///vfV+RxwwAFlnnnmqY6M0yI8RKpSrYgGSXCESHG55ZYrm222WXnyySfLHXfcUQUKJ8fpxcK2RRddtKZeN9poo+rkXC+6ueGGG8oDDz","xQyYKdHDenucEGG9TMAScapKV8pHXfffdVUnKea9UXDppjXHXVVcsaa6xR59eV7zrYcayI1VzqMsssU+2II9r/4IMPlssvv7wKJY4fkTtPeewhjNijPORjzhkxONgvZQsfJOaabbbZ5ltCozlMQwCwf5VVVqmkYI5/9913H3ZdTNv0JgCC/IlC2KifffoffshD/yBrpO","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","AoArBiUNeLCoFCoBAoBAqByUGgCMDk9EXVpBAoBAqBQqAQWDEEigCsGNT1okKgECgECoFCYHIQKAIwOX1RNSkECoFCoBAoBFYMgSIAKwZ1vagQKAQKgUKgEJgcBIoATE5fVE0KgUKgECgECoEVQ6AIwIpBXS8qBAqBQqAQsAmRrbAvv/zy7hrXuEZ39atfve0WOYubRj","lvwa6Ytl7+4x//2F3zmtds52NMylUEYFJ6oupRCBQChcCMI2D7YUrx3HPP7U488cTukY98ZLfddtu1Uwln8VCiX/ziF933v//97otf/GL3wx/+sHvc4x7X3e52t5uYXt6IAPzoRz/qfvazn7WOuOpVr9pd73rXa//Wab/85S/bCV/+fZWrXKUxmboKgUJgvAj8/ve/73","71q191P//5z9uJgawHxwe7CElnB1z72tfuNttss786yc3cdM6AuXrZZZe1+eszCZe6ffOb32xCkCWUrYJZfvai1y6W4HWuc53WroVYSZ5tb/tvf/vbTblsscUWTUY5iriu+SNA9vtQWmS9saR/6AD9oX9ucIMbdFe+8pUXraz1lf4/9dRTu4MPPrh72tOe1u2+++7t+f","PtL3W7+OKL2/36er7lRiGhbj/4wQ9a2691rWu1MeSshKVcP/nJT7pvfetb3Sc+8YlW5/3226+7173utZRHjrXsRgTgox/9aPe5z32udfCtb33r7oEPfGD7t4Hw1a9+tTEZ/77ZzW7W3e1udxtrRephhUAh0LU5Zq6df/753YUXXtiIwH/+5382aJBuAvie97xnd8c73r","G7xS1usZGipPyQhgsuuKD75Cc/2ebvgx70oImAldw4+uijuw9+8IPN/UsJuBwWRPhf97rX7W5/+9t3W2+9dRPq2jnfC0n66U9/2h177LHdb3/7226PPfbobn7zmzcBXtf8EEDI1q9f333+85/vvvzlL7dxCEv9Qwfojzvc4Q7dNtts0934xjdu3y3mGgcBYKiecMIJrX","/33HPPsZ2oaUx+4AMf6H784x+3Obb55ps3IrCU0MRUEQCN//SnP906l5vioQ99aAPZBCNQvvOd77T/3/a2t20A1VUIFALjQYCwZSFQ3l/60peatU8xEsAsMEKIEjUXEQKx03vc4x5N0d3whjdslWC5fO9732sE4gtf+EL3sIc9rH0m4VL3173udd1ZZ53VBCtLnxeD4i","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","b3IQV+cz57SMKqVLpeWggUAoVAITDzCGxEAH71q191v/71r5tCdrG8r371q7fPXBcF9tvf/rZ9HHCSI04pb+VZtp5L4VNsV7va1ZqFe+mll3Zf/OIXm7L3+6Mf/ejuDne4Qzs97GMf+1h3+umndze/+c3bd/e5z326a17zmkvuDHWliNWVh4EXwkXZIh45nEW9l2KBs+","op9O9+97utnT/84Q+bt8N1netcp7vJTW7S3epWt2qK/6KLLuouv/zyVh/vvP71r98Iz41udKNGgOoqBAqBQqAQKASWA4GNCMCFF17YffOb32xWOWV03etet7vd7W7X3eY2t5nz3cgCRXfxxRe3D+VJyd3jHvdoyozio+S/9KUvNSufcrvqVa/afetb32qK/jOf+UxThP","vss0933/vet1nAJ554YveGN7yhKX/fPeYxj2nPWuqlrr/85S+773znO837gAhwzaszy1y9b3azm7W/SMFiSYBn/uAHP+je8Y53dJ/73OcaLsgHcuTZd7/73bsnPvGJ3ec///l2D+L0hz/8oTXvlre8ZXfve9+7e8hDHtLd9a53XbJdgJkAACAASURBVGqTq3whUAgUAo","VAITAUgY0IwLnnntt9+tOfbsqZQmL5P/zhD++23377pgyHKcSf/exn3Uc/+tGm4L/3ve+1MptvvnlT2ixdFvCnPvWp7qyzzmqWPOWHUHz/+99v5bwPAXjWs57VlD1ycMIJJ3RHH310t+WWW3Zbb71199jHPraFBxZzscYp+m9/+9utXT68EbwOObGMYkYC5CEgHbe//e","3bR10Wc7HokZ6TTz65tZNXA5mi/IUykByE4wtf+EJ35plnNlJ0vetdr3khhDyQgNve9rbdTW9608W8vsoUAoVAIVAIFAIjEdiIAHzgAx/oPvzhDzdlzlKmFHfffffuaU97WotZU5T9i6VLwR977LHNkv/xj3/c3NYU/F577dVc+BdccEH3wQ9+sHv/+9/fPfShD+0e9K","AHNUVPSS6GAFDaXPcsajFz4YQk1g1rLSJDCfM2sMa/+tWvbnD5x8pHEjzPdZe73KXbaqutum222aYpbZff8tFmFyzyUY9cnvWNb3yjveuUU05pSX73vOc9G7HYYostWp1/8pOftPDH+eef3zwuj3vc47p73etejSggUMId/vp/2pokwbTZu/vv7bddXZVTRn0RN/cHp8","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","v/jTfeqF566aVCjumfeeaZp+igmNvu/4c//KF68803qyeeeKIQiDnmmKOadtppq0knnbQzQBygp3jllVeqUaNGFYIF756OGWaYoZp77rmrqaaaaoyTVb+GDXnttdeKzHzIgEzJa8IJJyynvvjii9W9995bxkEzMjj99NMXfTjbbLOVcZFH/yMwFgF49913i8CeffbZYv","w//PDDMiBiz2jCm3zyyasFFligWmqppQoLx9Da4dBPE/+pp56qnnnmmdLHddddtwzQofSoKa277767euGFF4pyanaYHAhLfIaStLSDPLMPnYUAgn7ddddVDzzwQPXBBx8Uz7DxoEvMg9VXX71aa621SqTg7bffru67775CHJBoHqRzFltssUICZpllluKFmv9PPvnkmM","+8885b7oEsRDShsxDtv6d5+OGHq0svvbQQLSSgp4NuXXnllatZZ521mmaaacY6XaSTI3b77bcXXcyGIHOrrbZahTiQHXvC+J9zzjklMtoVASBnpGHhhRcutiZJXE9SGbffxyIAhHLXXXcV4SECYYR4o0JsDn/XWGONapdddile9pRTTjmm5fo58WV4snWPtn5eV99HO/","Vru/KI3SfuJaR09dVXV3fccUf12GOPVcsvv3y10047FQZJCcTRVR/95v49tVF/plY99Mcff7y64oorqkceeaSQgGYRiamnnrpaaaWVysfkipBX/RlbbX9crhm34ZNXJQKtIcCYn3LKKdU111xTcTQo/kaSy8EwD7baaqtq++23L6mzRx99tDr22GNLVM/5DD2isOSSS1","arrLJKIfkIPu//4osvLmSb87LOOutU++yzTzk3UwDdy+imm26qjjvuuEq0Upqmq4ORDr2ywgorVFtuuWUx0PRr/RA95vSceuqpheyJHjP+W2+9dTXXXHMV+RoLdOJPfvKTMg5C99Z1Kpkx/CuuuGK19tprl2hDHv2PQCEA2DlBnXfeecWIYnCY2qKLLlqYl5y/VIDcGk","bHS91oo43GigBErtsgwvAjaiBiwACHEdaOQYbNm6jC3QaFNj/77LPShns5jxHUD2yeJ19XGAajezz33HNjSICwkzAjIkM5UADajXeuu6/ncJ2BKvzknhSIAaYfwpCRp9JP5/lQMM7VDyEtXkUrYak6AZCeWHXVVUtoEqb1Aa8PGLIPYuUwOVwDG33RL+cJjcFFX+uYwN","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","IADLEAsvlEIBFIBBKBRGAoEEgCMBSoZ5uJQCKQCCQCicAQI5AEYIgFkM0nAolAIpAIJAJDgUASgKFAPdtMBBKBRCARSASGGIEkAEMsgGw+EUgEEoFEIBEYCgSSAAwF6tlmIpAIJAKJQCIwxAgkARhiAWTziUAikAgkAonAUCCQBGAoUM82E4FEIBFIBBKBIUagWwLw97","//vfrTn/5UPv/93/9d/fWvf63+9re/Vf/v//2/6p/+6Z+qf/3Xf63GH3/88vef//mfB+VR/vjHP5b+6Ms3vvGNauKJJy796evx1VdflWf88ssvK8896aSTlvuPN9541UC12dc+5/WJwHBF4C9/+UuZx+abf08yySTVt771repf/uVfypyLg77xuzn4X//1X0Xv0Dn/9m","//NmZ+OtecpROc99lnn5X7TDjhhEU3mcd59A6BP//5z9Uf/vCHohP/53/+p5piiikKnq0cZEYOZOvvN7/5zWqiiSYqcgg7QV4+ZOo8+tf/yXeCCSYobfl3f+j2Vvo8Us9pSgAIgyDfeeed6o033qg+/vjjMiBMRpOLkKaeeurymXbaacvkrU/cgQL09ddfL33Sl8knn7","xaaKGF+mWCUxoffvhh9corr5SBuPjii1eTTTZZGYSe/+233x7T5oILLliUUB6JQCIwbgh88skn1VtvvVXmm7m3yCKLVDPPPHMxFOZcHAyQc837F198segdxmjeeect8z8MBF31+eefV2+++Wb12GOPFcdgjjnmqKabbroyj/PoHQLvv/9+9fTTT1cffPBBIWorrbRSwb","OVgzF/9dVXi8x8yGD++ecv8goSgVQ4j/ydS8a+Y0dmm2220pZ/J3lrBfFxP+drBIAQTCQG7/nnny+T9L333itMzUDwe7Bwk3WaaaapFl544Wr22Wevpp9++rEm77h3q/mV9957b/Xkk0+W/hgka6+9dhkofT1eeOGF6vHHH6+eeeaZolQ22GCD8kwUyf3331898cQTha","kanNqkiPJIBBKB3iHAI6RbRo8eXeYU3cJ4r7/++oUEcCh4iUg4A+Hcu+++uxgKRonuYdAXW2yxQgLMRw4JomD+uudTTz1VzTfffNU666xTjA49lUfPCNDt5PPSSy9Vzz77bNGzn376adGHO+20U7Xsssv2eBORg48++qi67bbbyvWcKk7a6quvXs0444xFdiI1r732Wp","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","kABgvpbCcRSAQSgUQgEWgjBDqOANiC9NNPPy2brVjnOsUUU1QTTTRRG0GeXUkEEoFEIBFIBIYegY4jAO+//371yCOPVJ988knZbGW55Zar5p577qFHOnuQCCQCiUAikAi0EQKFAHz00UfVG2+8UXlJiV3KWj1mmmmmauqppy67fY3LblJ2pHr99dcr7U833XTVpJNOWo","0//vjjdK/o88svv1xdeeWV1VtvvVWiANtuu221wgortPpIeV4ikAgkAiMeAVtVv/fee+WdARyp2WabrURTGw/vfHj11Verzz//vLz7oavDC4cmmWSSavLJJ+/y3ReuY3c4bewBO9TKwV7MPPPMlXdqsD/xki32RL/837soRICnnHLKll9M5dq33367+sMf/lBeajXLLL","NU0047beWtlp32ZstCAJ555pnqtttuK/uTM5qtHquttlq12GKLFWB7+0Yp4XlC1672l1122WqOOeaoJptssj7tHf7kk09WJ598cvXSSy9Vf/rTn6rvfve71UYbbdTqI+V5iUAikAiMWAToZQb/zTffrB599NFCAhjB9dZbr1pggQW+hssHH3xQ3XDDDdVrr73W1HlkPG","edddZq/vnnr2aYYYav3SPeHvn8889Xt956a7FDrRxzzTVXtcYaa1QTTzxxIQAff/xxcfxGjx5d/v3ll18W0jH99NOXtjmrPb2dkk3yzPfff3/B4LPPPqvWWmutaqmllurIba0LAbj77rurM888szAw7KfVY8cdd6zWXnvtwuq+8Y1vtHpZOY9w5OovvvjiatSoUdVmm2","1WLbnkkoVp9WV/9CQAvRJDnpwIJAKJQEHA+xt48lKojz/+ePXUU0+NeWnVvvvuW6288spjIcVY8v5POumk6umnn24aAZh99tmrRRZZpFpxxRWreeaZ52touw978MADD1RnnHFGsUOtHEsvvXS16667FtvDUN9+++2lHyIASIvnEVFGEEQKllhiiWrVVVdt+oIq/RCBYE","NuvPHGEgUQRdhtt92KE9mJ700oBADwF1xwQQGR1xwG+sMPPyz/ByTmJuRSP7baaqsCKAKAgTnP+cDH6nwHNKzLR/gkXkSBZfHSL7nkksI0t9xyy8KypAK8oUroJtIBhCAy4RMpCkIX+nFePf3QWwKA7eqzv/rsWdw3j0QgEUgERgICEY195513KinUO+64o3riiSeKV0","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","kcxpZ5PM93nrncnD4+83q+UMp1c9MsGxbhG9frafOiru3IzbaU64lb1JXlkgOz7RPKb0N9MIUAYDE/9thjzccffxyd3ttjoYUWCgs7ybu35ZyXQuORRx4Ji32eeeZpZphhhtgadUJEwJNPPtmcddZZzQsvvBD3s8ceezRrr712X5pW5xYChUAhMKIReO+995rnnnuuef","/992Nb5yWWWKKZZZZZPoFJkqfzXn755eaDDz6IFw3hhC984QvNjDPO2Ew99dTNFFNM0Ws8kTEeevHFF5s333wzvLmIGDfMNttszfTTT9+t4UmAeMnWSy+9FO1WzsuOppxyymb22WdvJptssk/svElcqO+NN96I9rdfkoSPlBubeOj1TQ3RE0MA3H///c2ll17a/OEPf4","g9ont7bLzxxs2KK64YHQOkvhw62MC64oormoceeqhZc801G4Jimmmm6fO12vW61m9/+9vmmWeeiXs55JBDmk033bQvTatzC4FCoBAYcQggc17Tt99+u3n22WcbvPDOO+8EQW611VbNUkstNQYmyNMciziJhaeffrohHIRhCYDJJ5+8mXnmmZu55547DDwE7N0DPR25RT","EyRuKMOcZhXg+ZzzfffM28887bzDrrrHE9xqJ2aHe246mnnmrefffdKEeETDvttM38888fxqpySejuKwUDvvBRJt+SSGwo568wcidumxwC4Nprr21+85vfRGdTTb099t133yBXHc1F1JeD2DDQTj311Oauu+5qtt9++2bllVcOlWnwjO9RAmB8katyhUAhMJIRYJQhzu","uuu665++67G95ZBI94v/GNb0Q4tX04H+mee+65zX333de8/vrrQcTt91yYywmHNdZYI0KxLOqeDhY8o/Caa65pLr744rDKeRRcz8H1zypfcMEFmy233LJZYIEF4mVT2W7tuOOOO6IdvL/KIW2igydi1VVXjXJEgXapC18oR7wIGyN/h3JTTTVV1LfFFltE+9XVae/uCA","Hw6KOPRqdTP0BxEAOp6HTq0ksvHUqofay11lrxvXd+5xvAuF6oKkACC/jcPz7iMXke987DDz8cHgD1b7jhhjFAKC0uI26bdNlwy/hon0HiUKffnduOCfVVABjg2uyva+twgqaOQqAQKARGAgKZAyYMbP685557whrmfkeu5tnvfve7MUe3DwRNJJx++ulhsTtvpplmCo","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","NzFhuFFcFMFJjH+6L6xWZphKjN1rrbKG28L14FhxUCJQCGVXdVYzsBgRIAndCLw/ceSgAM377r75ZP8tRTT5UHoL9RresVAmNBILdCzW1SOw2s3JbVXyGO9tarnXavw/V+bN/LU8ML1d4bf7jeT7V7/BCY5OOPPy4BMH7YValCYLwQyLeNIcZOdY/nm9g69f7Gq+OHUK","Hcr98YLIE2hDpmgJtSqwAGGPCqrhAoBAqBQqAQGAoIlAAYCr1QbSgECoFCoBAoBAYYgRIAAwx4VVcIFAKFQCFQCAwFBEoADIVeqDYUAoVAIVAIFAIDjEAJgAEGvKorBAqBQqAQKASGAgIlAIZCL1QbCoFCoBAoBAqBAUagBMAAA17VFQKFQCFQCBQCQwGBEgBDoReqDY","VAIVAIFAKFwAAjUAJggAGv6gqBQqAQKAQKgaGAQAmAodAL1YZCoBAoBAqBQmCAESgBMMCAV3WFQCFQCBQChcBQQKAEwFDohWpDIVAIFAKFQCEwwAiUABhgwKu6QqAQKAQKgUJgKCAwTgHgrVH//Oc/4+MNX/n2KH+9RnKovkpSu//xj3/E29Z82m+98r37mXTSScfrbW","xw8EpXn7y2zoSFa/Z05GtStS3fwOW7T33qU3GdNr7a5zzXy/qc55OH793L3/72tzHa4VpZLvuvXafyee/K+821HNo1vrgMhQFdbSgECoFCoBDoHQLjFABvv/128+qrrzavvPJK88c//jHeH/2Zz3ym+exnP9vMM888zUwzzdS7mgb4rDfeeKN58cUXmymnnLKZYoop4u","P9645HHnkk7meJJZZoZphhhj61DDEj/nvvvbd54IEHmmmnnbb53Oc+F0Q855xzNosttliP1/vzn//c3H777c2bb77ZTDbZZCFCXAuO00wzTfPXv/413p/ut6effrp5//33o42ura455pijWWihheL6SBvxP/roo81tt90W5T//+c/HNWeeeeYo96c//al56623mueff7","75wx/+MJrYCRXthM2zzz7bvPfee433gxMB+naRRRaJ+xqq4q5PHVYnFwKFQCFQCHSLwDgFAPJ48skng0wRylRTTRUXQlarrLJKM9988zUffPBBEFm+35wFylJNAsnvCQjEhayQGoJDyj7ILK1RRObz4YcfBjE5nEN8JPmpP61k5yJhJPfxxx/HOS+88ELzxBNPNHPPPX","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"

                                } );
                                String json = "{\n" +
                                        "    \"bill_id\": 23666,\n" +
                                        "    \"customer_name\": \"Guest\",\n" +
                                        "    \"order_collection\": \"[{\\\"id\\\":94,\\\"name\\\":\\\"Milk Coffee\\\",\\\"price\\\":15000,\\\"quantity\\\":1,\\\"type\\\":\\\"recipe\\\",\\\"purchprice\\\":5250,\\\"includedtax\\\":0,\\\"options\\\":[{\\\"optionName\\\":\\\"Sugar\\\",\\\"name\\\":\\\"Less Sugar\\\",\\\"type\\\":\\\"option\\\",\\\"price\\\":0,\\\"purchPrice\\\":0}],\\\"productNotes\\\":\\\"\\\",\\\"original_price\\\":15000,\\\"original_purchprice\\\":5250}]\",\n" +
                                        "    \"total\": 15000,\n" +
                                        "    \"users_id\": 10,\n" +
                                        "    \"states\": \"closed\",\n" +
                                        "    \"payment_method\": \"Cash\",\n" +
                                        "    \"split_payment\": null,\n" +
                                        "    \"delivery\": \"direct\",\n" +
                                        "    \"created_at\": \"2023-08-28T07:57:55.000000Z\",\n" +
                                        "    \"updated_at\": \"2023-08-28T07:58:23.000000Z\",\n" +
                                        "    \"deleted_at\": null,\n" +
                                        "    \"outlet_id\": 9,\n" +
                                        "    \"servicefee\": 5,\n" +
                                        "    \"gratuity\": 1,\n" +
                                        "    \"vat\": 11,\n" +
                                        "    \"customer_id\": null,\n" +
                                        "    \"bill_discount\": 0.67,\n" +
                                        "    \"table_id\": null,\n" +
                                        "    \"total_discount\": 100,\n" +
                                        "    \"hash_bill\": \"fb4d7a85e19ad82a9d65536a7d5837a2\",\n" +
                                        "    \"reward_points\": \"{\\\"initial\\\":0,\\\"redeem\\\":0,\\\"earn\\\":0}\",\n" +
                                        "    \"total_reward\": 0,\n" +
                                        "    \"reward_bill\": 0,\n" +
                                        "    \"c_bill_id\": \"1359\",\n" +
                                        "    \"rounding\": 469,\n" +
                                        "    \"isQR\": 0,\n" +
                                        "    \"notes\": null,\n" +
                                        "    \"amount_paid\": 18000,\n" +
                                        "    \"totaldiscount\": 100,\n" +
                                        "    \"totalafterdiscount\": 14900,\n" +
                                        "    \"cashier\": \"premium1staff1\",\n" +
                                        "    \"totalgratuity\": 149,\n" +
                                        "    \"totalservicefee\": 745,\n" +
                                        "    \"totalbeforetax\": 15794,\n" +
                                        "    \"totalvat\": 1737,\n" +
                                        "    \"totalaftertax\": 17531,\n" +
                                        "    \"rounding_setting\": 500,\n" +
                                        "    \"totalafterrounding\": 18000,\n" +
                                        "    \"div\": 1,\n" +
                                        "    \"bill_date\": \"Mon August 28 2023 14:57:55\",\n" +
                                        "    \"pos_bill_date\": \"Mon August 28 2023 14:57:55\",\n" +
                                        "    \"pos_paid_bill_date\": \"Mon August 28 2023 14:58:23\",\n" +
                                        "    \"rewardoption\": \"true\",\n" +
                                        "    \"return\": 0\n" +
                                        "}";
                                String json2 = "{\n" +
                                        "    \"orders_id\": 765,\n" +
                                        "    \"bill_id\": 23668,\n" +
                                        "    \"created_at\": \"2023-08-30T07:06:41.000000Z\",\n" +
                                        "    \"updated_at\": \"2023-08-30T07:06:41.000000Z\",\n" +
                                        "    \"deleted_at\": null,\n" +
                                        "    \"listorders\": \"[{\\\"name\\\":\\\"Fried Rice\\\",\\\"quantity\\\":1,\\\"table_name\\\":\\\"-\\\",\\\"options\\\":[{\\\"name\\\":\\\"Extra Cheese\\\",\\\"type\\\":\\\"extra\\\",\\\"price\\\":15000,\\\"purchPrice\\\":9000},{\\\"optionName\\\":\\\"Sauce\\\",\\\"name\\\":\\\"BBQ Sauce\\\",\\\"type\\\":\\\"option\\\",\\\"price\\\":0,\\\"purchPrice\\\":0}],\\\"productNotes\\\":\\\"test\\\"}]\",\n" +
                                        "    \"states\": \"submitted\",\n" +
                                        "    \"outlet_id\": 9,\n" +
                                        "    \"notes\": null,\n" +
                                        "    \"c_bill_id\": \"1361\"\n" +
                                        "}\n";
//                                Log.e("formatted", parseJsonFormatBillNonEpson(json, ""));

                                EscPosPrinter printer = new EscPosPrinter(new UsbConnection(usbManager, usbDevice), 203, 48f, 32);
//                                parseImageHex(printer, img, true);
//                                parseImageHex(printer, img2, false);
                                printer.printFormattedText(
                                        parseJsonFormatOrderNonEpson(json2, "")
//                                                parseImageHex(printer, img, true)
//                                                        +
////                                                        "[L]\n" +parseImageHex(printer, img2, false)
//                                                        parseJsonFormatBillNonEpson(json, "")
//                                                "[L]\n" +
//                                                "[C]<font size='tall'>Jalan Simpang Dago!</font>\n" +
//                                                "[L]<font size='normal'>Bill No: 1357</font>\n" +
//                                                "[L]<font size='normal'>Created: 28/08/2023 11:18</font>\n" +
//                                                "[L]<font size='normal'>Paid: 28/08/2023 11:18</font>\n" +
//                                                "[L]<font size='normal'>Cashier: premium1staff1</font>\n" +
//                                                "[L]<font size='normal'>Customer Name: Guest</font>\n" +
//                                                "[L]\n" +
//                                                "[C]<u>                                             </u>\n" +
//                                                "[L]\n" +
//                                                "[L]<b>Pizza</b>[R]260.000\n" +
//                                                "[L]  + BBQ Sauce (0)\n" +
//                                                "[L]  + Regular Crust (0)\n" +
//                                                "[L]  + Grilled Onions (7.000)\n" +
//                                                "[L]  + Extra Mushrooms (3.000)\n" +
//                                                "[L]  + Notes : Extra mushroom on plastic\n" +
//                                                "[L]\n" +
//                                                "[L]<b>Milk Coffee</b>[R]15.000\n" +
//                                                "[L]  + Less Sugar (0)\n" +
//                                                "[L]  + Notes : dont add ice\n" +
//                                                "[L]\n" +
//                                                "[C]<u>                                             </u>\n" +
//                                                "[R]TOTAL :[R]275.000\n" +
//                                                "[R]CASH :[R]275.000\n" +
//                                                "[L]\n" +
//                                                "[C]<u>                                             </u>\n" +
//                                                "[L]\n" +
//                                                "[L]<font size='normal'>Powered by ReBill POS</font>\n" +
//                                                "[C]Follow us on @rebillpos!!\n" +
//                                                "[L]\n"
                                );
                            } catch (EscPosConnectionException e) {
                                throw new RuntimeException(e);
                            } catch (EscPosEncodingException e) {
                                throw new RuntimeException(e);
                            } catch (EscPosBarcodeException e) {
                                throw new RuntimeException(e);
                            } catch (EscPosParserException e) {
                                throw new RuntimeException(e);
                            } catch (JSONException e) {
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

    private String parseImageHex(EscPosPrinterSize printer, String base64, boolean isLogo){
        if(isLogo) {
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
            int newWidth = 480;
            int newHeight = Math.round(newWidth / aspectRatio);

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

        //todo use extra data
        int isNumberFormat = 1; //todo change
        boolean isCompact = true; //todo change
        String lang = "id"; //todo change

        HashMap<String, String> textLabel = getTextHashMap(lang);

        int ordersId = obj.getInt("orders_id");
        String billId = obj.getString("c_bill_id");
        output += "[L]<font size='big'>Order no : "+ordersId+"</font>\n";
        output += "[L]\n";
        output += "[L]<font size='normal'>"+textLabel.get("billnolabel")+": "+billId+"</font>\n";

        String posBillDate = obj.getString("created_at");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
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

            JSONArray options = item.getJSONArray("options");
            for (int j = 0; j < options.length(); j++) {
                JSONObject optItem = options.getJSONObject(j);
                String optName = optItem.getString("name");
                output += "[L]  + " + optName +"\n";
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

        //todo header & footer section
        output += "[L]\n";
        output += "[C]<font size='tall'>Jalan Simpang Dago!</font>\n";

        int isNumberFormat = 1; //todo change
        boolean isCompact = true; //todo change
        String lang = "id"; //todo change

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

            JSONArray options = item.getJSONArray("options");
            for (int j = 0; j < options.length(); j++) {
                JSONObject optItem = options.getJSONObject(j);
                String optName = optItem.getString("name");
                int optPrice = optItem.getInt("price");
                output += "[L]  + " + optName + " (" + commaSeparateNumber(optPrice+"",isNumberFormat,div) + ")\n";
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

        String invFooter = "Follow us on @rebillpos!!"; //todo change
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
        var dot = '.';
        var comma = ',';
        // remove sign if negative
        var sign = 1;
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

//        if (String.valueOf(val).contains("\\.")) {
//            var dec = String.valueOf(val).split("\\.")[1];
//            if (String.valueOf(val).split("\\.")[1].length() < 2) {
//                dec = String.valueOf(val).split("\\.")[1] + '0';
//            }
//            num = num + (isNumberFormat == 1 ? comma : dot) + dec;
//        } else {
//            if (currencyDecimal == '0') {
//                num = num + (isDecimal ? (isNumberFormat == 1 ? comma : dot) + "00" : "");
//            } else {
//                num = num + (isNumberFormat == 1 ? comma : dot) + "00";
//            }
//        }

        // return result with - sign if negative
        return sign < 0 ? '-' + num : num;
    }


    /**
     * print using wifi (as for now not sure if we can detect other wifi printers other than epson)
     * @param name : printer name. we need to get the IP address only from the name
     * @param text : text to print
     */
    public void printWifi(String name, String text) {
        String[] names = name.split("\\(");
        String ipAddress = names[0];
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
    private boolean runPrintReceiptSequence(String data, String extraData) {
        //first we need to create the receipt means we need to parse it using epson format by calling createReceiptData
        if (!createReceiptData(data, extraData)) {
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
    private boolean createReceiptData(String data, String extraData) {
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
//            mPrinter.

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
//            textData.append(text+"\n");
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
