package com.ai.assistance.operit.ui.main

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.ai.assistance.operit.data.security.PrivacyPurpose
import kotlinx.coroutines.launch

/** Activity-owned platform prompts: a recreated host cannot accept an old host's authentication. */
internal class PrivacyAuthenticator(private val activity:ComponentActivity,private val vm:CompanionViewModel) {
    private var signal:CancellationSignal?=null
    private var legacyTicket:String?=null
    private val legacy=activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result->
        val id=legacyTicket;legacyTicket=null
        if(id!=null){if(result.resultCode==Activity.RESULT_OK)success(id)else vm.privacy.cancel(id)}
    }
    fun deviceSecure()=activity.getSystemService(KeyguardManager::class.java).isDeviceSecure
    fun request(purpose:PrivacyPurpose) {
        if(!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))return
        vm.privacy.resumed(deviceSecure())
        val ticket=vm.privacy.begin(purpose)?:return
        val title=when(purpose){PrivacyPurpose.ENABLE->"开启隐私锁";PrivacyPurpose.DISABLE->"关闭隐私锁";PrivacyPurpose.UNLOCK->"解锁星语"}
        try {
            if(Build.VERSION.SDK_INT>=30) {
                val cancellation=CancellationSignal();signal=cancellation
                BiometricPrompt.Builder(activity).setTitle(title).setSubtitle("使用系统身份验证，应用不会读取你的锁屏密码")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build().authenticate(cancellation,activity.mainExecutor,object:BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){signal=null;success(ticket.id)}
                        override fun onAuthenticationError(errorCode:Int,errString:CharSequence){signal=null;vm.privacy.cancel(ticket.id,"AUTH_ERROR")}
                        override fun onAuthenticationFailed(){ /* The system prompt owns retry and rate limiting. */ }
                    })
            } else {
                @Suppress("DEPRECATION")
                val intent=activity.getSystemService(KeyguardManager::class.java).createConfirmDeviceCredentialIntent(title,"确认手机锁屏凭据后继续")
                    ?:throw IllegalStateException("UNAVAILABLE")
                legacyTicket=ticket.id;legacy.launch(intent)
            }
        }catch(_:Exception){signal=null;legacyTicket=null;vm.privacy.cancel(ticket.id,"AUTH_ERROR")}
    }
    private fun success(id:String) {
        activity.lifecycleScope.launch {
            activity.lifecycle.withResumed {
                if(deviceSecure())vm.privacyAuthenticated(id)else vm.privacy.cancel(id,"UNAVAILABLE")
            }
        }
    }
    fun dispose(){signal?.cancel();signal=null;legacyTicket=null;vm.privacy.cancel()}
}
