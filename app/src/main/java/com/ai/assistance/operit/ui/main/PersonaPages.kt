package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.RoleEntity
import com.ai.assistance.operit.data.model.UserPersona

@Composable internal fun PersonaEditorDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    // Keep the opened version: a concurrent role edit must not silently replace this draft.
    val original=remember(role.scope()){role}
    var name by remember {mutableStateOf(original.userName)}
    var description by remember {mutableStateOf(original.userPersona)}
    var enabled by remember {mutableStateOf(original.personaEnabled)}
    var preview by remember {mutableStateOf(false)}
    var discard by remember {mutableStateOf(false)}
    val changed=name!=original.userName||description!=original.userPersona||enabled!=original.personaEnabled
    val valid=name.length<=UserPersona.NAME_LIMIT&&name.none(Char::isISOControl)&&description.length<=UserPersona.DESCRIPTION_LIMIT&&(!enabled||name.isNotBlank())
    val leave={if(!state.working){if(changed)discard=true else onDismiss()}}
    Dialog(onDismissRequest=leave,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=leave,enabled=!state.working){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
                    Column(Modifier.weight(1f)){Text("用户面具",style=MaterialTheme.typography.titleLarge);Text("${role.name} 所认识的你",style=MaterialTheme.typography.bodySmall)}
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text("给这个角色一个专属的称呼和用户设定。其他角色不会自动看到。",style=MaterialTheme.typography.bodyLarge)
                    OutlinedTextField(name,{if(it.length<=UserPersona.NAME_LIMIT)name=it},Modifier.fillMaxWidth(),label={Text("希望 TA 怎样称呼你")},singleLine=true,enabled=!state.working,
                        supportingText={Text("${name.length}/${UserPersona.NAME_LIMIT}")})
                    OutlinedTextField(description,{if(it.length<=UserPersona.DESCRIPTION_LIMIT)description=it},Modifier.fillMaxWidth(),label={Text("关于你的设定（可选）")},minLines=5,maxLines=12,enabled=!state.working,
                        supportingText={Text("${description.length}/${UserPersona.DESCRIPTION_LIMIT} · 可以填写喜好、背景或相处方式")})
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)){Text("在这个角色中启用");Text("从下一次发送或重试起提供给所选模型",style=MaterialTheme.typography.bodySmall)}
                        Switch(enabled=!state.working,checked=enabled,onCheckedChange={enabled=it})
                    }
                    if(enabled&&name.isBlank())Text("启用前请填写称呼。",color=MaterialTheme.colorScheme.error)
                    if(role.isRaw())Text("当前使用原味模型，这份面具会保留但不会注入请求；切回角色模式后继续生效。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    Text("关闭后保留设定但不再注入新请求。在途回复保持原设定，已有聊天中提及的内容仍属于历史。修改这里不会自动删除历史消息。",style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={preview=!preview},enabled=valid,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(preview)"收起提示词预览"else"预览发送给模型的设定")}
                    if(preview&&valid) {
                        Text("当前草稿的系统提示词",style=MaterialTheme.typography.titleSmall)
                        Text(if(role.isRaw())"当前原味模式没有应用系统提示词，保存面具也不会注入请求。切回角色模式后才能在完整预览中看到它。"else"预览使用实际聊天的基础提示词构建器，保存后才生效。这里不包含世界书、会话历史、工具目录和待发送正文；完整设定注入可在世界书中选择会话预览。",style=MaterialTheme.typography.bodySmall)
                        Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium) {
                            SelectionContainer {Text(RequestContext.systemPrompt(original.copy(userName=name.trim(),userPersona=description,personaEnabled=enabled)),Modifier.padding(16.dp))}
                        }
                    }
                    Text("角色复制及公开 JSON 模板不包含用户面具；个人加密备份会包含这份设定。",style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={name="";description="";enabled=false},enabled=!state.working&&(name.isNotEmpty()||description.isNotEmpty()||enabled)){Text("清空这份面具草稿",color=MaterialTheme.colorScheme.error)}
                    Spacer(Modifier.height(8.dp))
                }
                Button(onClick={vm.savePersona(original,name,description,enabled,onDismiss)},enabled=valid&&!state.working,
                    modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=48.dp)){Text(if(state.working)"正在保存…"else"保存用户面具")}
            }
        }
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的修改？")},text={Text("已保存的用户面具保持不变。")},
        confirmButton={TextButton(onClick=onDismiss){Text("放弃修改")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
}
