package com.gastech.pos;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
import com.gastech.sunmi.SunmiPrinterPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // سجّل الـ Plugins المخصصة قبل super.onCreate
        registerPlugin(SunmiPrinterPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
