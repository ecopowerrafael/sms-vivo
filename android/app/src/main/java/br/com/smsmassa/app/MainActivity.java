package br.com.smsmassa.app;

import com.getcapacitor.BridgeActivity;
import br.com.smsmassa.app.sms.SmsPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(SmsPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
