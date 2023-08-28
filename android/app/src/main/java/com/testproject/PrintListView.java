package com.testproject;

import android.content.Context;
import android.widget.RelativeLayout;

public class PrintListView extends RelativeLayout {
    private Context context;

    public PrintListView(Context context) {
        super(context);
        this.context = context;
    }

    public void init() {
        //Part 1: Don't need to copy BONUS part, this alone already integrate Android UI to RN native.
        inflate(this.context, R.layout.act_printer_list, this);
    }
}
