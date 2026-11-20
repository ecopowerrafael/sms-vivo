package br.com.smsmassa.app.sms

import com.getcapacitor.PluginCall
import com.getcapacitor.annotation.PermissionCallback
import org.junit.Assert.assertNotNull
import org.junit.Test

class PermissionCallbackContractTest {
    @Test
    fun everyPermissionAliasHasACapacitorCallback() {
        val callbackNames = listOf(
            "smsPermission",
            "phoneStatePermission",
            "notificationPermission",
            "contactsPermission"
        )

        callbackNames.forEach { name ->
            val method = SmsPlugin::class.java.getDeclaredMethod(name, PluginCall::class.java)
            assertNotNull("$name precisa existir", method)
            assertNotNull("$name precisa ter @PermissionCallback", method.getAnnotation(PermissionCallback::class.java))
        }
    }
}
