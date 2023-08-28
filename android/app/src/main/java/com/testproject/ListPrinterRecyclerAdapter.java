package com.testproject;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ListPrinterRecyclerAdapter extends RecyclerView.Adapter<ListPrinterRecyclerAdapter.ViewHolder> {

    public static final String USB_TYPE = "usb";
    public static final String BLUETOOTH_TYPE = "bt";
    public static final String WIFI_TYPE = "wifi";
    public static final String EPSON_TYPE = "epson";
    private LayoutInflater mInflater;
    Context ctx;
    List<Printer> items;
    ActionListener listener;

    public interface ActionListener {
        void onItemClick(Printer p);
    }

    public ListPrinterRecyclerAdapter(Context context, List<Printer> items, ActionListener listener) {
        this.ctx = context;
        this.items = items;
        this.mInflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        // Create a new view, which defines the UI of the list item
        LinearLayout v = (LinearLayout) LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.item_printer, viewGroup, false);
        ViewHolder vh = new ViewHolder(v);

        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Printer item = items.get(position);
        holder.bind(item);
        holder.parent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public LinearLayout parent;
        public ViewHolder(LinearLayout v) {
            super(v);
            parent = v;
        }

        public void bind(Printer item){
            TextView txtPrinterType = (TextView) parent.findViewById(R.id.txtPrinterType);
            switch(item.type){
                case USB_TYPE:
                    txtPrinterType.setText("USB Printer");
                    break;
                case BLUETOOTH_TYPE:
                    txtPrinterType.setText("Bluetooth Printer");
                    break;
                case EPSON_TYPE:
                    txtPrinterType.setText("Epson Printer");
                    break;
                case WIFI_TYPE:
                    txtPrinterType.setText("Wifi Epson Printer");
                    break;
            }
            TextView txtPrinterName = (TextView) parent.findViewById(R.id.txtPrinterName);
            txtPrinterName.setText(item.name);
        }
    }
}