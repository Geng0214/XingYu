package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.TextMaterials

@Composable
internal fun MaterialPreviewDialog(state:CompanionState,vm:CompanionViewModel) {
    val material=state.materialPreview ?: return
    AlertDialog(
        onDismissRequest=vm::dismissMaterialPreview,
        title={
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                    Icon(if(material.kind==TextMaterials.KIND_LINK)Icons.Default.Link else Icons.Default.Description,null,Modifier.padding(10.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("资料预览")
                    Text(material.displayName,style=MaterialTheme.typography.bodySmall,fontWeight=FontWeight.Normal,maxLines=2,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text={
            Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(
                    if(material.kind==TextMaterials.KIND_LINK)"已联网读取此 HTTPS 地址；确认后下方文字才会随你的下一条消息发给模型。"
                    else "已从本机读取此文件；确认后下方文字才会随你的下一条消息发给模型。",
                    style=MaterialTheme.typography.bodyMedium
                )
                material.sourceUrl?.let {url->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("来源地址",style=MaterialTheme.typography.labelLarge)
                        SelectionContainer {Text(url,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick={},enabled=false,label={Text(if(material.mimeType==TextMaterials.MIME_HTML)"网页正文" else if(material.mimeType==TextMaterials.MIME_MARKDOWN)"Markdown" else "纯文本")})
                    AssistChip(onClick={},enabled=false,label={Text("${material.text.length} 字")})
                }
                if(material.truncated) {
                    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top) {
                            Icon(Icons.Default.WarningAmber,null,tint=MaterialTheme.colorScheme.onErrorContainer)
                            Text("原文共 ${material.originalChars} 字，超过单次资料上限。这里只会加入你看到的前 ${material.text.length} 字。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                Text("将加入模型上下文的文字",style=MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    Text(material.text,Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(14.dp)).padding(14.dp),style=MaterialTheme.typography.bodyMedium)
                }
                Text("请检查其中是否含私人信息。资料内容只作为参考，不会被当成应用命令；链接、脚本和代码不会由应用执行。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={Button(enabled=!state.working,onClick=vm::confirmMaterialPreview){Text("加入待发送消息")}},
        dismissButton={TextButton(onClick=vm::dismissMaterialPreview){Text("取消")}}
    )
}
