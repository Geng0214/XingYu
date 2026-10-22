package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.MessageExportFormat
import com.ai.assistance.operit.data.model.MessageExports

@Composable
internal fun MessageExportPreviewDialog(state:CompanionState,vm:CompanionViewModel,onSave:(MessageExportFormat,String)->Unit) {
    val preview=state.messageExportPreview?:return
    var format by remember(preview.messageId){mutableStateOf(MessageExportFormat.MARKDOWN)}
    var baseName by remember(preview.messageId){mutableStateOf(preview.suggestedBaseName)}
    val valid=MessageExports.isValidBaseName(baseName)
    AlertDialog(
        onDismissRequest={if(!state.working)vm.dismissMessageExport()},
        icon={Icon(Icons.Default.SaveAlt,null)},
        title={Text("保存 AI 回复")},
        text={Column(Modifier.heightIn(max=600.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("将保存这条完整回复",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Text("角色：${preview.roleName}\n会话：${preview.chatTitle}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                MessageExportFormat.entries.forEach {item->FilterChip(selected=format==item,onClick={format=item},label={Text(item.label)})}
            }
            OutlinedTextField(value=baseName,onValueChange={if(it.length<=MessageExports.MAX_BASE_NAME)baseName=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("文件名（不含扩展名）")},supportingText={
                Text(if(valid)"实际文件名：${MessageExports.fileName(baseName,format)}" else "不能含路径符号、控制字符或首尾空格")
            },isError=!valid,enabled=!state.working)
            Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("UTF-8 · ${preview.byteCount} 字节 · ${format.mimeType}",style=MaterialTheme.typography.labelMedium)
                    HorizontalDivider()
                    SelectionContainer {Text(preview.content,style=MaterialTheme.typography.bodyMedium)}
                }
            }
            Text("请检查回复中的私人信息。确认后会通过系统文件选择器直接写入你选择的位置，并在回读内容完全一致后提示成功；应用不会创建公共目录缓存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},
        confirmButton={Button(enabled=valid&&!state.working,onClick={onSave(format,baseName)}){Text("选择位置并保存")}},
        dismissButton={TextButton(enabled=!state.working,onClick=vm::dismissMessageExport){Text("取消")}}
    )
}
