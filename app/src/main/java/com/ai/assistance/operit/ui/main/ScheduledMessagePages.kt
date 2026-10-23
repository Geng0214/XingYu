package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.RoleEntity
import com.ai.assistance.operit.data.model.ScheduledMessage
import com.ai.assistance.operit.data.model.ScheduledMessages
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val scheduledFormat=DateTimeFormatter.ofPattern("M月d日 HH:mm")

private fun scheduledStateText(item:ScheduledMessage)=when(item.state){
    ScheduledMessages.STATE_PENDING->"待发送"
    ScheduledMessages.STATE_COMPLETE->"已发送"
    ScheduledMessages.STATE_CANCELLED->"已取消"
    else->"发送失败"
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun ScheduledMessageDialog(role:RoleEntity,chatId:String?,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val items by produceState(initialValue=emptyList<ScheduledMessage>(),role.id,role.spaceId) {
        vm.scheduledMessages(role.scope()).collect {value=it}
    }
    var content by remember{mutableStateOf("")}
    var mode by remember{mutableStateOf(ScheduledMessages.MODE_TEXT)}
    var configId by remember{mutableStateOf<String?>(null)}
    var date by remember{mutableStateOf(LocalDate.now())}
    var hour by remember{mutableStateOf("20")}
    var minute by remember{mutableStateOf("00")}
    var pickDate by remember{mutableStateOf(false)}
    val zone=ZoneId.systemDefault()
    fun dueAt():Long? {
        val h=hour.toIntOrNull()?:return null
        val m=minute.toIntOrNull()?:return null
        if(h !in 0..23||m !in 0..59)return null
        return LocalTime.of(h,m).atDate(date).atZone(zone).toInstant().toEpochMilli()
    }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("定时消息")},
        text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("固定文本到点直接发出，不联网；生成模式会让角色按指令写一条。",style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(mode==ScheduledMessages.MODE_TEXT,{mode=ScheduledMessages.MODE_TEXT},label={Text("固定文本")})
                    FilterChip(mode==ScheduledMessages.MODE_GENERATE,{mode=ScheduledMessages.MODE_GENERATE},label={Text("让角色生成")})
                }
                if(mode==ScheduledMessages.MODE_GENERATE) {
                    if(state.configs.isEmpty())Text("还没有模型连接，无法使用生成模式。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    else FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        state.configs.forEach {config->FilterChip(configId==config.id,{configId=config.id},label={Text(config.name)})}
                    }
                }
                OutlinedTextField(content,{if(it.length<=(if(mode==ScheduledMessages.MODE_TEXT)ScheduledMessages.MAX_TEXT else ScheduledMessages.MAX_INSTRUCTION))content=it},Modifier.fillMaxWidth(),label={Text(if(mode==ScheduledMessages.MODE_GENERATE)"给角色的指令" else "内容")},minLines=2,enabled=chatId!=null)
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={pickDate=true},enabled=chatId!=null){Text("${date.monthValue} 月 ${date.dayOfMonth} 日")}
                    OutlinedTextField(hour,{if(it.length<=2)hour=it},Modifier.width(72.dp),label={Text("时")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=chatId!=null)
                    OutlinedTextField(minute,{if(it.length<=2)minute=it},Modifier.width(72.dp),label={Text("分")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=chatId!=null)
                }
                if(chatId==null)Text("先开始一次聊天，才能安排定时消息。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                else if(dueAt()==null||dueAt()!!<=System.currentTimeMillis())Text("请选择将来的时间。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                if(items.isEmpty())Text("还没有定时消息。",style=MaterialTheme.typography.bodySmall)
                items.forEach {item->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.content.replace('\n',' ').take(40),maxLines=1,style=MaterialTheme.typography.bodyMedium)
                            Text("${scheduledFormat.format(Instant.ofEpochMilli(item.dueAt).atZone(zone))} · ${if(item.mode==ScheduledMessages.MODE_GENERATE)"生成" else "固定文本"} · ${scheduledStateText(item)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if(item.state==ScheduledMessages.STATE_PENDING)TextButton(onClick={vm.cancelScheduledMessage(role.scope(),item.id)}){Text("取消")}
                    }
                }
            }
        },
        confirmButton={
            TextButton(enabled=chatId!=null&&content.isNotBlank()&&dueAt()!=null&&dueAt()!!>System.currentTimeMillis()&&(mode==ScheduledMessages.MODE_TEXT||configId!=null),onClick={
                val due=dueAt()?:return@TextButton
                vm.saveScheduledMessage(role.scope(),chatId!!,content,due,mode,configId){content=""}
            }){Text("安排")}
        },
        dismissButton={TextButton(onClick=onDismiss){Text("关闭")}}
    )
    if(pickDate) {
        val picker=rememberDatePickerState(initialSelectedDateMillis=date.atStartOfDay(zone).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={pickDate=false},confirmButton={
            TextButton(onClick={
                picker.selectedDateMillis?.let {date=Instant.ofEpochMilli(it).atZone(zone).toLocalDate()}
                pickDate=false
            }){Text("确定")}
        },dismissButton={TextButton(onClick={pickDate=false}){Text("取消")}}) {
            DatePicker(state=picker)
        }
    }
}
