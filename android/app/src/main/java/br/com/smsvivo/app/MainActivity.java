package br.com.smsvivo.app;

import com.getcapacitor.BridgeActivity;
import br.com.smsvivo.app.sms.SmsPlugin;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        registerPlugin(SmsPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
