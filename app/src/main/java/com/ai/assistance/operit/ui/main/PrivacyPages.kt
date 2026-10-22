package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.data.security.PrivacyPurpose
import com.ai.assistance.operit.data.security.PrivacyState

internal val LocalPrivacyAuthentication=staticCompositionLocalOf<(PrivacyPurpose)->Unit> {error("Missing authentication host")}

@Composable internal fun PrivacyLockedPage(state:PrivacyState) {
    val authenticate=LocalPrivacyAuthentication.current
    val focus=LocalFocusManager.current;val keyboard=LocalSoftwareKeyboardController.current
    val context=LocalContext.current
    LaunchedEffect(Unit){focus.clearFocus(force=true);keyboard?.hide()}
    Surface(Modifier.fillMaxSize().semantics {contentDescription="隐私锁屏"},color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().navigationBarsPadding().statusBarsPadding().padding(28.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Default.Lock,null,Modifier.size(52.dp),tint=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            Text("星语",style=MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(when {state.error=="STORAGE"->"无法读取隐私锁设置。请保留应用和数据，不要卸载或清空。"
                !state.loaded->"正在读取保护设置…"
                state.saving->"正在保存保护设置…"
                state.ticket!=null->"请完成系统身份验证"
                !state.deviceSecure->"请先在系统设置中配置锁屏密码，再返回解锁。"
                else->"验证身份后，回到你的聊天空间。"},style=MaterialTheme.typography.bodyLarge)
            if(state.error in setOf("CANCELLED","AUTH_ERROR")) {Spacer(Modifier.height(12.dp));Text("验证未完成，内容保持锁定。",style=MaterialTheme.typography.bodyMedium)}
            Spacer(Modifier.height(28.dp))
            if(!state.loaded||state.saving)CircularProgressIndicator()
            else if(state.error!="STORAGE"&&state.ticket==null) {
                if(state.deviceSecure)Button(onClick={authenticate(PrivacyPurpose.UNLOCK)},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("解锁")}
                else OutlinedButton(onClick={context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))},Modifier.heightIn(min=48.dp)){Text("打开系统安全设置")}
            }
        }
    }
}

@Composable internal fun PrivacySettingsPage(vm:CompanionViewModel,onBack:()->Unit) {
    val state by vm.privacy.state.collectAsStateWithLifecycle()
    val authenticate=LocalPrivacyAuthentication.current
    val context=LocalContext.current
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
            Text("隐私与数据",style=MaterialTheme.typography.headlineSmall)
        }
        Icon(Icons.Default.Lock,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
        Text("让聊天只在你面前打开",style=MaterialTheme.typography.titleLarge)
        Text("开启后，冷启动和切到后台返回都需要系统身份验证。启动验证或锁定都会停止生成、插件和连接测试，并隐藏聊天及工具确认。")
        Text("离开页面后未保存的表单可能需要重新填写。已选择的本机文件会等待解锁再处理；不会在锁定时导出角色内容。",style=MaterialTheme.typography.bodyMedium)
        Text(if(state.enabled)"隐私锁已开启"else"隐私锁未开启",style=MaterialTheme.typography.titleMedium)
        Button(onClick={authenticate(if(state.enabled)PrivacyPurpose.DISABLE else PrivacyPurpose.ENABLE)},Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(if(state.enabled)"验证身份并关闭"else"验证身份并开启")
        }
        if(state.enabled)OutlinedButton(onClick={vm.privacy.lockNow()},Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("立即锁定")}
        if(!state.deviceSecure||state.error=="UNAVAILABLE") {
            Text("先设置手机锁屏密码，才能启用隐私锁。",color=MaterialTheme.colorScheme.error)
            TextButton(onClick={context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))}){Text("系统安全设置")}
        }
        HorizontalDivider()
        Text("本机数据保护",style=MaterialTheme.typography.titleMedium)
        Text("聊天、人设、图片与插件内容继续使用 SQLCipher 加密；API Key 独立保护。隐私锁控制应用访问，不改变已有数据库密钥。系统备份和截图继续关闭。")
        Text("可在设置中的“加密备份”保存个人备份。请勿在确认备份可恢复前卸载或清空数据；修改系统锁屏后若验证不可用，请先恢复系统认证条件。",style=MaterialTheme.typography.bodyMedium)
    }
}
