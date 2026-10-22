package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.backup.BackupSummary
import com.ai.assistance.operit.data.backup.HomeRestoreMode
import java.text.DateFormat
import java.util.Date

@Composable internal fun BackupPage(state:CompanionState,vm:CompanionViewModel,onBack:()->Unit) {
    val files=LocalFileActions.current
    var keys by remember {mutableStateOf(false)}
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")};Text("加密备份",style=MaterialTheme.typography.headlineSmall)}
        Icon(Icons.Default.EnhancedEncryption,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
        Text("把陪伴妥善保存",style=MaterialTheme.typography.titleLarge)
        Text("备份角色、人设、对话模式、用户面具、关系、长期记忆、世界书、会话、消息、图片、文字资料、伴读书架与进度、收藏、背景头像、模型配置和插件。文件由独立口令加密，可在另一台安装星语的手机上恢复。")
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(checked=keys,onCheckedChange={keys=it},enabled=!state.working)
            Column(Modifier.weight(1f)){Text("同时备份 API Key");Text("默认不包含。启用后，持有文件和口令的人可以使用这些 Key。",style=MaterialTheme.typography.bodySmall)}
        }
        Button(onClick={vm.previewBackup(keys)},enabled=!state.working,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("预览并创建备份")}
        HorizontalDivider()
        Text("从备份恢复",style=MaterialTheme.typography.titleMedium)
        Text("先选文件、输入口令，再核对内容。恢复会追加独立副本，不覆盖已有角色和聊天；同一个备份重复恢复也会新增副本。")
        OutlinedButton(onClick=files.importBackup,enabled=!state.working,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("选择备份文件")}
        Text("恢复的扩展及工具授权保持关闭，隐私锁仍按本机设置执行。不含 Key 的备份需要重新填写模型和服务凭据。",style=MaterialTheme.typography.bodyMedium)
        Text("请使用独立长口令并另行保管，遗忘后无法找回。保存位置由系统文件选择器决定；选择云盘会把加密文件交给相应服务。",style=MaterialTheme.typography.bodyMedium)
        Text("可以读取本项目旧版 .cbackup 备份。最多 32 MiB 内容、10 万条记录。未包含手机系统密钥或隐私锁设置。生成中的回复、图片、语音、提炼或游戏回合会先停止，已完成内容仍保存。",style=MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun BackupCounts(summary:BackupSummary) {
    Text("备份时间：${DateFormat.getDateTimeInstance().format(Date(summary.createdAt))}")
    Text("${summary.roles} 个角色 · ${summary.chats} 段会话 · ${summary.messages} 条消息")
    Text("${summary.models} 个模型配置 · ${summary.images} 张图片 · ${summary.extensions} 个扩展")
    Text("${summary.materials} 份 TXT、Markdown 或链接文字资料")
    Text("${summary.stickerAssets} 个表情素材 · ${summary.stickerMessages} 条表情消息")
    Text("${summary.imageGenerationConfigs} 个生图服务 · ${summary.generatedImages} 条生图记录")
    Text("${summary.speechServiceConfigs} 个语音服务配置（录音与合成音频不进入备份）")
    Text("${summary.readingDocuments} 份伴读原文（包含章节与本机阅读进度）")
    Text("${summary.sharedActivities} 次共同活动（包含会话、进度和感想）")
    Text("${summary.musicTracks} 个本机音视频条目、${summary.musicSessions} 次播放记录。包含名称、进度和感想；原文件及系统读取授权不在备份中，恢复后需重新选择原文件。")
    Text("${summary.bilibiliWatches} 次 B 站观看记录。保存视频身份、进度和感想，不保存网页缓存、Cookie 或登录状态；恢复后不会自动连接网站。")
    Text("${summary.groups} 个群、${summary.groupMessages} 条群消息。恢复为独立群身份，未完成请求标为中断，不自动发言。")
    Text("${summary.playSessions} 个剧情或游戏存档、${summary.playRequests} 条角色回合记录。保留局面与剧情；恢复后不自动继续生成。")
    Text("${summary.homes} 个共享家园、${summary.homeCommands} 条家园操作。包含田地、背包、家具、居民位置、赠礼和穿搭。")
    Text("${summary.personas} 份用户面具设定（包括已关闭但保留内容的面具）")
    Text("${summary.worldbookEntries} 条世界书设定（保留启用状态和会话范围）")
    Text("${summary.relationships} 条关系设定（保留启用状态、优先级和会话范围）")
    Text("${summary.memories} 条长期记忆（保留类型、开关和优先级）")
    Text("${summary.diaries} 篇日记与心事（保留用户填写或模型生成来源）")
    Text("${summary.environments} 份时间与天气设定（包含明确选择的城市、开关和最后快照）")
    Text("${summary.plannerEntries} 条日程/待办/纪念日 · ${summary.cycleRecords} 条经期记录")
    Text("${summary.proactiveSettings} 个主动消息设置 · ${summary.proactiveRequests} 条主动请求记录；恢复后角色开关全部关闭")
    Text(if(summary.includesCredentials)"包含 ${summary.credentials} 个 API Key，已由备份口令保护。"else"不包含 API Key，恢复后需要重新填写。")
}

@Composable internal fun BackupDialogs(state:CompanionState,vm:CompanionViewModel) {
    val backup=state.backup?:return
    val files=LocalFileActions.current
    var password by remember(backup.stage){mutableStateOf("")}
    var repeat by remember(backup.stage){mutableStateOf("")}
    var acknowledged by remember(backup.stage){mutableStateOf(false)}
    var homeMode by remember(backup.stage){mutableStateOf<HomeRestoreMode?>(null)}
    val creating=backup.stage=="EXPORT_PREVIEW"
    val decrypting=backup.stage=="RESTORE_PASSWORD"
    val preview=backup.stage=="RESTORE_PREVIEW"
    AlertDialog(onDismissRequest={if(!state.working)vm.dismissBackup()},title={Text(when(backup.stage){
        "EXPORT_PREVIEW"->"创建加密备份";"EXPORT_READY"->"选择保存位置";"RESTORE_PASSWORD"->"解密备份";"RESTORING"->"正在恢复备份";else->"确认恢复内容"
    })},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        backup.summary?.let {BackupCounts(it)}
        if(creating||decrypting) {
            Text(if(creating)"设置 12～128 个字符的独立口令。我们不保存口令，遗忘后无法恢复。"else"输入创建此备份时的口令。验证成功后会先显示预览，不会立即导入。")
            OutlinedTextField(password,{if(it.length<=128)password=it},Modifier.fillMaxWidth(),label={Text("备份口令")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),enabled=!state.working)
            if(creating)OutlinedTextField(repeat,{if(it.length<=128)repeat=it},Modifier.fillMaxWidth(),label={Text("再次输入口令")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),enabled=!state.working)
        }
        if(preview) {
            Text("追加独立副本，保留本机全部数据。原有身份全部重新分配；中断请求不会自动续发。插件文件仅保存，恢复时不执行脚本或连接服务。")
            Text("导入的扩展与角色授权均关闭。使用前请核对模型和 MCP 服务地址；本机隐私锁设置不变。")
            if(backup.summary?.homes==1) {
                Text("家园恢复方式")
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=homeMode==HomeRestoreMode.IMPORT_WORLD,onClick={homeMode=HomeRestoreMode.IMPORT_WORLD},enabled=!backup.homeExists&&!state.working)
                    Text(if(backup.homeExists)"本机已有共享家园，不能再导入第二个世界"else"导入备份家园，保留其中的田地、物品和居民状态")
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=homeMode==HomeRestoreMode.JOIN_CURRENT,onClick={homeMode=HomeRestoreMode.JOIN_CURRENT},enabled=!state.working)
                    Text("仅追加角色与聊天，让新角色入住本机家园；不导入备份家园的物品和进度")
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(acknowledged,{acknowledged=it},enabled=!state.working);Text("我确认这是自己信任的个人备份")}
        }
        if(backup.stage=="EXPORT_READY")Text("加密内容已经准备好。请选择文件位置；保存结束后会回读核对。系统选择器返回时，如启用了隐私锁，需要先解锁。")
        if(state.working){LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在处理，请稍候。",style=MaterialTheme.typography.bodySmall)}
    }},confirmButton={TextButton(enabled=!state.working&&when{creating->password.length in 12..128&&password==repeat;decrypting->password.length in 12..128;preview->acknowledged&&(backup.summary?.homes!=1||homeMode!=null);else->backup.stage=="EXPORT_READY"},onClick={
        when {creating->{val value=password;password="";repeat="";vm.encryptBackup(value,files.exportBackup)}
            decrypting->{val value=password;password="";vm.decryptBackup(value)}
            preview->vm.restoreBackup(if(backup.summary?.homes==1)requireNotNull(homeMode)else HomeRestoreMode.JOIN_CURRENT)
            else->files.exportBackup()
        }
    }){Text(when{creating->"加密并选择位置";decrypting->"验证并预览";preview->"追加恢复";else->"保存文件"})}},dismissButton={TextButton(onClick={if(state.working)vm.cancelBackup()else vm.dismissBackup()}){Text(if(state.working)"停止处理"else"取消")}})
}
