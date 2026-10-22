package com.ai.assistance.operit

import android.content.ContextWrapper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.security.*
import com.ai.assistance.operit.ui.main.MainActivity
import com.ai.assistance.operit.ui.main.CompanionViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PrivacyLockAndroidTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    @Test fun policyIsEncryptedIndependentAndCorruptionDoesNotResetOtherKeys() {
        val directory=File(context.cacheDir,"privacy-test-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(context){override fun getNoBackupFilesDir()=directory}
        try {
            val vault=CredentialVault(isolated)
            val key=vault.databaseKey(false)
            val ref=vault.save("synthetic-private-api-key","https://example.invalid/chat")
            assertFalse(vault.privacyLockEnabled())
            vault.setPrivacyLock(true);assertTrue(CredentialVault(isolated).privacyLockEnabled())
            assertArrayEquals(key,vault.databaseKey(true))
            assertEquals("synthetic-private-api-key",vault.read(ref,"https://example.invalid/chat"))
            val policy=File(directory,"vault/privacy-lock.enc");val encoded=policy.readBytes()
            assertTrue(encoded.size>29);assertNotEquals("true",encoded.toString(Charsets.UTF_8))
            val corrupt=encoded.copyOf();corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte();policy.writeBytes(corrupt)
            try {vault.privacyLockEnabled();fail("Corrupt policy must not disable lock")}catch(_:java.security.GeneralSecurityException){}
            assertArrayEquals(corrupt,policy.readBytes());assertArrayEquals(key,vault.databaseKey(true))
            policy.writeBytes(encoded);vault.setPrivacyLock(false);assertFalse(vault.privacyLockEnabled())
        }finally {directory.deleteRecursively()}
    }
    private fun text(node:AccessibilityNodeInfo?):String {
        if(node==null)return ""
        val result=StringBuilder().append(node.text).append(' ').append(node.contentDescription)
        repeat(node.childCount){result.append(' ').append(text(node.getChild(it)))}
        return result.toString()
    }
    private fun awaitScreen(locked:Boolean) {
        val deadline=SystemClock.uptimeMillis()+10_000
        do {
            instrumentation.waitForIdleSync()
            val content=text(instrumentation.uiAutomation.rootInActiveWindow)
            if(locked&&content.contains("隐私锁屏"))return
            if(!locked&&!content.contains("隐私锁屏")&&content.contains("设置")&&content.contains("角色"))return
            SystemClock.sleep(50)
        }while(SystemClock.uptimeMillis()<deadline)
        fail("Expected privacy screen locked=$locked")
    }
    /** UI/lifecycle test injects a trusted success into the gate; it does NOT test system biometrics. */
    @Test fun coldStartAndBackgroundRemovePrivateUiAndDeferPickerResults() {
        val vault=CredentialVault(context);val original=vault.privacyLockEnabled();vault.setPrivacyLock(true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                awaitScreen(true)
                var vm:CompanionViewModel?=null;var delivered=0
                scenario.onActivity {activity->
                    val model=ViewModelProvider(activity)[CompanionViewModel::class.java];vm=model
                    assertFalse(model.privacy.state.value.canAccess)
                    model.receiveExternalResult{delivered++};assertEquals(0,delivered)
                    model.privacy.resumed(true)
                    val ticket=model.privacy.begin(PrivacyPurpose.UNLOCK)!!
                    runBlocking {assertTrue(model.privacy.authenticated(ticket.id){fail()})}
                }
                awaitScreen(false)
                scenario.onActivity {assertEquals(1,delivered)}
                scenario.moveToState(Lifecycle.State.STARTED)
                assertFalse("Losing foreground must lock before onStop",vm!!.privacy.state.value.canAccess)
                scenario.moveToState(Lifecycle.State.CREATED)
                assertFalse(vm!!.privacy.state.value.canAccess)
                scenario.moveToState(Lifecycle.State.RESUMED)
                awaitScreen(true)
                scenario.onActivity {activity->
                    val model=ViewModelProvider(activity)[CompanionViewModel::class.java]
                    assertFalse(model.privacy.state.value.canAccess);assertEquals(1,delivered)
                }
            }
        } finally {vault.setPrivacyLock(original)}
    }
}
