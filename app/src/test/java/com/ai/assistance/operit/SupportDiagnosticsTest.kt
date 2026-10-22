package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class SupportDiagnosticsTest {
    @Test fun onboardingProgressUsesRealMilestones() {
        val empty=OnboardingProgress(0,1,0,0);assertEquals(1,empty.completedSteps);assertFalse(empty.done)
        val complete=OnboardingProgress(1,1,1,1);assertEquals(4,complete.completedSteps);assertTrue(complete.done)
    }
    @Test fun diagnosticContainsOnlyBoundedAllowlistedMetadata() {
        val report=SupportDiagnostics.create("AUTH",123,"0.12.0",36,1,2,3,true,
            listOf("DeepSeek","https://private.invalid/v1?key=SECRET","PRIVATE_MODEL"))
        val text=report.render()
        assertEquals(RecoveryRoute.CONNECTIONS,SupportDiagnostics.route("AUTH"));assertEquals(RecoveryRoute.HELP,SupportDiagnostics.route("NETWORK"))
        assertTrue(text.contains("error=AUTH"));assertTrue(text.contains("providers=deepseek,other"))
        listOf("SECRET","private.invalid","PRIVATE_MODEL","https://").forEach {assertFalse(text.contains(it))}
        assertEquals("UNKNOWN",SupportDiagnostics.create("bad detail: SECRET",1,"v",1,0,0,0,false,emptyList()).errorCode)
    }
}
