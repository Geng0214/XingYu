package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.api.audio.SpeechEndpoint
import com.ai.assistance.operit.api.audio.CommunicationDevice
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ai.assistance.operit.data.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SpeechServiceSettingsPage(state:CompanionState,vm:CompanionViewModel,onBack:()->Unit) {
    BackHandler(onBack=onBack)
    var selected by remember {mutableStateOf<String?>(state.speechServiceConfigs.firstOrNull()?.id)}
    val config=state.speechServiceConfigs.firstOrNull {it.id==selected}
    var kind by remember(selected,config?.version){mutableStateOf(config?.kind?:SpeechServices.TRANSCRIPTION)}
    var endpoint by remember(selected,config?.version){mutableStateOf(config?.endpoint?:"")}
    var model by remember(selected,config?.version){mutableStateOf(config?.model?:if(kind==SpeechServices.TRANSCRIPTION)"whisper-1" else "gpt-4o-mini-tts")}
    var voice by remember(selected,config?.version){mutableStateOf(config?.voice?:"alloy")}
    var timeout by remember(selected,config?.version){mutableStateOf((config?.timeoutSeconds?:120).toString())}
    var key by remember(selected,config?.version){mutableStateOf("")}
    var issue by remember {mutableStateOf("")}
    var confirm by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
            Column {Text("语音服务",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text("录音识别与 AI 回复朗读使用独立配置",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                state.speechServiceConfigs.forEach {FilterChip(selected==it.id,{selected=it.id},label={Text(it.name)})}
                AssistChip(onClick={selected=null;kind=SpeechServices.TRANSCRIPTION;endpoint="";model="whisper-1";voice="alloy";key=""},label={Text("＋ 新服务")})
            }
            Text("用途",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(kind==SpeechServices.TRANSCRIPTION,{kind=SpeechServices.TRANSCRIPTION;if(config==null)model="whisper-1"},label={Text("录音转文字")})
                FilterChip(kind==SpeechServices.SYNTHESIS,{kind=SpeechServices.SYNTHESIS;if(config==null)model="gpt-4o-mini-tts"},label={Text("文字转语音")})
            }
            OutlinedTextField(endpoint,{endpoint=it;issue=""},Modifier.fillMaxWidth(),label={Text("HTTPS 语音地址")},placeholder={Text("https://api.example.com/v1")},singleLine=true,
                supportingText={Text("接受域名、/v1 或完整 ${if(kind==SpeechServices.TRANSCRIPTION)"/audio/transcriptions" else "/audio/speech"} 地址。")})
            OutlinedTextField(model,{if(it.length<=200)model=it},Modifier.fillMaxWidth(),label={Text("模型")},singleLine=true)
            if(kind==SpeechServices.SYNTHESIS)OutlinedTextField(voice,{if(it.length<=100)voice=it},Modifier.fillMaxWidth(),label={Text("声音")},placeholder={Text("alloy")},singleLine=true)
            OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={Text("API Key")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,
                supportingText={Text(if(config==null)"独立加密保存，只发往确认的语音地址。" else "已保存的 Key 不回显；地址不变时可留空。")})
            OutlinedTextField(timeout,{if(it.length<=3)timeout=it},Modifier.fillMaxWidth(),label={Text("请求超时（15–300 秒）")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            if(issue.isNotEmpty())Text(issue,color=MaterialTheme.colorScheme.error)
            Button(enabled=!state.working,onClick={
                try {
                    val normalized=SpeechEndpoint.complete(endpoint,kind)
                    val candidate=SpeechServiceConfig(selected?:"preview","预览",kind,normalized,model.trim(),if(kind==SpeechServices.SYNTHESIS)voice.trim() else "",timeout.toInt(),"pending",1)
                    SpeechServices.validateConfig(candidate);require(key.isNotBlank()||(config!=null&&config.endpoint==normalized));issue="";confirm=true
                }catch(_:Exception){issue="请检查用途、HTTPS 地址、模型、声音、超时和 Key。"}
            }){Icon(Icons.Default.Mic,null);Spacer(Modifier.width(8.dp));Text("保存语音服务")}
            Text("聊天页点击麦克风，选择设备后开始录音，最长 60 秒。停止后还要再次确认目的地才会上传；识别文字只回填输入框。合成语音只在消息菜单中手动请求，不会写入文件或数据库。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("确认语音目的地")},text={Text("${if(kind==SpeechServices.TRANSCRIPTION)"录音 WAV" else "所选 AI 回复文字"}与 API Key 将发送到：\n\n${SpeechEndpoint.complete(endpoint,kind)}\n\n模型：${model.trim()}${if(kind==SpeechServices.SYNTHESIS)"\n声音：${voice.trim()}" else ""}\n\n请确认该地址由你信任的提供方运营。")},
        confirmButton={TextButton(onClick={confirm=false;vm.saveSpeechServiceConfig(selected,kind,endpoint,model,voice,timeout.toInt(),key){selected=it;key=""}}){Text("确认并保存")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
}

@Composable internal fun VoiceTranscriptionDialog(state:CompanionState,vm:CompanionViewModel,onSettings:()->Unit) {
    val recording=state.recordedVoice?:return
    val configs=state.speechServiceConfigs.filter {it.kind==SpeechServices.TRANSCRIPTION}
    var selected by remember(recording){mutableStateOf(configs.firstOrNull()?.id)}
    val config=configs.firstOrNull {it.id==selected}
    AlertDialog(onDismissRequest={if(!state.transcribingVoice)vm.dismissRecordedVoice()},icon={Icon(Icons.Default.Mic,null)},title={Text("语音预览")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("录音时长：${"%.1f".format(recording.durationMs/1000.0)} 秒 · 16 kHz 单声道")
            Text("可以直接发送为语音消息，或先识别成文字再编辑。发送上限 30 秒。",style=MaterialTheme.typography.bodySmall)
            if(configs.isEmpty()) {
                Text("还没有录音识别服务。录音仍只保存在内存中。")
                OutlinedButton(onClick=onSettings,modifier=Modifier.fillMaxWidth()){Text("打开语音服务设置")}
            } else {
                Text("选择识别服务",style=MaterialTheme.typography.labelLarge)
                configs.forEach {item->FilterChip(selected==item.id,{selected=item.id},label={Text(item.name)})}
                if(config!=null)Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(config.model,fontWeight=FontWeight.SemiBold);Text(config.endpoint,style=MaterialTheme.typography.bodySmall)
                        Text("确认后上传 WAV；识别结果只放入可编辑输入框。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if(state.transcribingVoice)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp);Text("正在识别…")}
        }
    },confirmButton={Row {
        TextButton(enabled=!state.transcribingVoice,onClick={vm.sendVoice {}}){Text("发送语音")}
        if(config!=null)TextButton(enabled=!state.transcribingVoice,onClick={vm.transcribeVoice(config.id)}){Text("识别成文字")}
    }},dismissButton={
        TextButton(onClick={if(state.transcribingVoice)vm.stopSpeech()else vm.dismissRecordedVoice()}){Text(if(state.transcribingVoice)"停止" else "丢弃")}
    })
}

@Composable internal fun VoiceCaptureStartDialog(role:RoleEntity,chat:ChatEntity,vm:CompanionViewModel,onDismiss:()->Unit) {
    var input by remember(role.id,chat.id){mutableStateOf<CommunicationDevice?>(null)}
    var output by remember(role.id,chat.id){mutableStateOf<CommunicationDevice?>(null)}
    AlertDialog(onDismissRequest=onDismiss,icon={Icon(Icons.Default.Mic,null)},title={Text("选择录音设备")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("本次录音用于 ${role.name} 的当前聊天。先选择设备，出现“正在录音”后再开口。")
            VoiceAudioPicker(vm,input,output,{input=it},{output=it})
            Text("通信输出用于建立耳麦连接，本次录音不会播放回复。准备设备时可能采样并丢弃缓冲；录音最多 60 秒，停止后先预览，再确认识别服务才会上传。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },confirmButton={TextButton(enabled=voiceDevicePairReady(input,output),onClick={
        onDismiss();vm.startVoiceRecording(role.scope(),chat.id,requireNotNull(input),requireNotNull(output))
    }){Text("连接并录音")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable internal fun SingleAudioStatus(state:CompanionState,vm:CompanionViewModel) {
    val label=when {
        state.voiceRecording->if(state.voiceRecordingReady)"正在录音 · 最长 60 秒" else "正在连接麦克风，请稍候"
        state.speakingMessageId!=null->when(state.singleSpeechStage) {
            SingleSpeechStage.CONNECTING->"正在连接播放设备"
            SingleSpeechStage.SYNTHESIZING->"正在合成这条回复的语音"
            SingleSpeechStage.PREPARING->"正在确认声音播放位置"
            SingleSpeechStage.PLAYING->"正在朗读回复"
            null->return
        }
        else->return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(label,Modifier.weight(1f).semantics {liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick={if(state.voiceRecording)vm.cancelVoiceCapture()else vm.stopSpeech()},modifier=Modifier.heightIn(min=48.dp)) {
            Text(if(state.voiceRecording)"取消录音" else "停止朗读")
        }
    }
}

@Composable internal fun SpeechPlaybackDialog(role:RoleEntity,messages:List<MessageEntity>,chatId:String,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val configs=state.speechServiceConfigs.filter {it.kind==SpeechServices.SYNTHESIS}
    var selected by remember(chatId,messages.firstOrNull()?.id){mutableStateOf(configs.firstOrNull()?.id)}
    val config=configs.firstOrNull {it.id==selected}
    val valid=messages.all {message->runCatching {SpeechServices.validateSpeechText(message.content)}.isSuccess}
    var output by remember(role.id,chatId,messages.firstOrNull()?.id) {mutableStateOf<CommunicationDevice?>(null)}
    AlertDialog(onDismissRequest=onDismiss,icon={Icon(Icons.Default.Mic,null)},title={Text("朗读后续消息")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("从选中的位置开始，按聊天顺序朗读 ${messages.size} 条完整消息。")
            Text(messages.firstOrNull()?.content.orEmpty(),maxLines=8)
            VoiceAudioPicker(vm,null,output,{},{output=it},includeInputs=false)
            if(!valid)Text("这条回复超过 4096 字符或包含无效字符，不能直接合成。",color=MaterialTheme.colorScheme.error)
            if(configs.isEmpty())Text("还没有文字转语音服务，请先在设置中保存独立配置。")
            else {
                Text("选择合成服务",style=MaterialTheme.typography.labelLarge)
                configs.forEach {item->FilterChip(selected==item.id,{selected=item.id},label={Text(item.name)})}
                if(config!=null)Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text("${config.model} · ${config.voice}",fontWeight=FontWeight.SemiBold);Text(config.endpoint,style=MaterialTheme.typography.bodySmall)
                        Text("确认后发送上方文字并请求 24 kHz PCM；只在内存中播放。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    },confirmButton={if(config!=null)TextButton(enabled=valid&&output!=null&&state.speakingMessageId==null,onClick={onDismiss();vm.speakMessages(role.scope(),chatId,messages.map {it.id},config.id,requireNotNull(output))}){Text("开始朗读")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable internal fun VoiceConversationStartDialog(role:RoleEntity,chat:ChatEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit,onStart:(String,String,CommunicationDevice,CommunicationDevice)->Unit) {
    val transcription=state.speechServiceConfigs.filter {it.kind==SpeechServices.TRANSCRIPTION}
    val synthesis=state.speechServiceConfigs.filter {it.kind==SpeechServices.SYNTHESIS}
    var transcriptionId by remember {mutableStateOf(transcription.firstOrNull()?.id)}
    var synthesisId by remember {mutableStateOf(synthesis.firstOrNull()?.id)}
    val selectedTranscription=transcription.firstOrNull {it.id==transcriptionId}
    val selectedSynthesis=synthesis.firstOrNull {it.id==synthesisId}
    var input by remember(role.id,chat.id) {mutableStateOf<CommunicationDevice?>(null)}
    var output by remember(role.id,chat.id) {mutableStateOf<CommunicationDevice?>(null)}
    AlertDialog(onDismissRequest=onDismiss,icon={Icon(Icons.Default.Call,null)},title={Text("与 ${role.name} 开始语音会话")},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("进入后会自动循环：录音 → 识别 → 发送到当前对话 → 朗读 AI 回复。每次说完需要点一下按钮，回复期间可以随时打断。")
            VoiceAudioPicker(vm,input,output,{input=it},{output=it})
            if(transcription.isEmpty()||synthesis.isEmpty())Text("需要各保存一个录音识别和文字转语音服务。",color=MaterialTheme.colorScheme.error)
            if(transcription.isNotEmpty()) {
                Text("录音识别",style=MaterialTheme.typography.labelLarge)
                transcription.forEach {item->FilterChip(transcriptionId==item.id,{transcriptionId=item.id},label={Text(item.name)})}
                selectedTranscription?.let {VoiceDestinationCard(it.model,it.endpoint,"每轮录音会自动上传到这里")}
            }
            if(synthesis.isNotEmpty()) {
                Text("回复朗读",style=MaterialTheme.typography.labelLarge)
                synthesis.forEach {item->FilterChip(synthesisId==item.id,{synthesisId=item.id},label={Text(item.name)})}
                selectedSynthesis?.let {VoiceDestinationCard("${it.model} · ${it.voice}",it.endpoint,"每轮 AI 回复会自动发送到这里")}
            }
            Text("会话只在当前聊天和前台有效。切换页面、锁定应用或进入后台会立即结束；识别文字和 AI 回复仍按普通聊天保存在加密数据库中，音频不落盘。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },confirmButton={TextButton(enabled=selectedTranscription!=null&&selectedSynthesis!=null&&voiceDevicePairReady(input,output),onClick={onStart(requireNotNull(transcriptionId),requireNotNull(synthesisId),requireNotNull(input),requireNotNull(output))}){Text("连接设备并开始")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable private fun VoiceDestinationCard(title:String,endpoint:String,note:String) {
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,fontWeight=FontWeight.SemiBold);Text(endpoint,style=MaterialTheme.typography.bodySmall)
            Text(note,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable internal fun VoiceConversationDialog(ui:VoiceConversationUi,vm:CompanionViewModel) {
    BackHandler {vm.endVoiceConversation()}
    var input by remember(ui.id,ui.stage) {mutableStateOf<CommunicationDevice?>(null)}
    var output by remember(ui.id,ui.stage) {mutableStateOf<CommunicationDevice?>(null)}
    val status=if(ui.reconnecting)"正在停止上一轮" else when(ui.stage) {
        VoiceConversationStage.CONNECTING->"正在连接音频设备"
        VoiceConversationStage.LISTENING->"正在听你说"
        VoiceConversationStage.TRANSCRIBING->"正在识别"
        VoiceConversationStage.GENERATING->"${ui.roleName} 正在回应"
        VoiceConversationStage.SYNTHESIZING->"正在准备声音"
        VoiceConversationStage.SPEAKING->"${ui.roleName} 正在说话"
        VoiceConversationStage.PAUSED->"语音会话已暂停"
        VoiceConversationStage.ENDED->"语音会话已结束"
    }
    Dialog(onDismissRequest=vm::endVoiceConversation,properties=DialogProperties(dismissOnBackPress=true,dismissOnClickOutside=false,usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=vm::endVoiceConversation){Icon(Icons.Default.KeyboardArrowDown,"返回文字聊天")}
                    Text("语音会话 · 第 ${ui.turn} 轮",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    IconButton(onClick=vm::endVoiceConversation){Icon(Icons.Default.CallEnd,"结束语音会话",tint=MaterialTheme.colorScheme.error)}
                }
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Surface(shape=CircleShape,color=if(ui.stage==VoiceConversationStage.PAUSED)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer) {
                        Box(Modifier.size(88.dp),contentAlignment=Alignment.Center) {
                            Icon(when(ui.stage){VoiceConversationStage.LISTENING->Icons.Default.Hearing;VoiceConversationStage.SPEAKING->Icons.AutoMirrored.Filled.VolumeUp;VoiceConversationStage.PAUSED->Icons.Default.ErrorOutline;else->Icons.Default.GraphicEq},null,Modifier.size(40.dp),tint=if(ui.stage==VoiceConversationStage.PAUSED)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Text(status,Modifier.semantics {liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
                    Text(when {
                        ui.reconnecting->"等待上一轮录音、请求和播放停止后再连接。"
                        ui.stage==VoiceConversationStage.CONNECTING->"正在准备麦克风和播放设备，请在显示‘正在听你说’后再开口。"
                        ui.stage==VoiceConversationStage.LISTENING->"说完后点“说完了”；最多录制 60 秒。"
                        ui.stage==VoiceConversationStage.PAUSED->errorText(ui.errorCode)
                        else->"可以点“打断并说话”停止当前步骤。"
                    },style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(ui.stage==VoiceConversationStage.CONNECTING||ui.reconnecting)LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("麦克风：${ui.inputName}\n播放到：${ui.outputName}",Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(ui.stage==VoiceConversationStage.PAUSED)VoiceAudioPicker(vm,input,output,{input=it},{output=it},enabled=!ui.reconnecting)
                    if(ui.transcript.isNotBlank())Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.secondaryContainer) {Column(Modifier.fillMaxWidth().padding(16.dp)) {Text("你说",style=MaterialTheme.typography.labelLarge);Text(ui.transcript)}}
                    if(ui.reply.isNotBlank())Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant) {Column(Modifier.fillMaxWidth().padding(16.dp)) {Text(ui.roleName,style=MaterialTheme.typography.labelLarge);Text(ui.reply)}}
                    if(ui.transcript.isBlank()&&ui.reply.isBlank())Text("本轮识别文字和回复会显示在这里，同时写入当前加密聊天。",Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("识别：${ui.transcriptionName}\n朗读：${ui.synthesisName}",Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    when(ui.stage) {
                        VoiceConversationStage.LISTENING->Button(onClick=vm::finishVoiceConversationUtterance,enabled=!ui.reconnecting,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Icon(Icons.Default.Stop,null);Spacer(Modifier.width(8.dp));Text("说完了")}
                        VoiceConversationStage.PAUSED->Button(onClick={vm.resumeVoiceConversation(requireNotNull(input),requireNotNull(output))},enabled=voiceDevicePairReady(input,output)&&!ui.reconnecting,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(8.dp));Text("使用所选设备继续")}
                        VoiceConversationStage.CONNECTING,VoiceConversationStage.ENDED->Unit
                        else->Button(onClick=vm::interruptVoiceConversation,enabled=!ui.reconnecting,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){Icon(Icons.Default.RecordVoiceOver,null);Spacer(Modifier.width(8.dp));Text("打断并说话")}
                    }
                    OutlinedButton(onClick=vm::endVoiceConversation,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Default.CallEnd,null);Spacer(Modifier.width(8.dp));Text("结束并返回文字聊天")}
                }
            }
        }
    }
}
