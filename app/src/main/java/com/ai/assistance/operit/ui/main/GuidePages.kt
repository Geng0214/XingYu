package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.OnboardingProgress

@Composable internal fun GuidePage(state:CompanionState,vm:CompanionViewModel,onConnections:()->Unit,onRoles:()->Unit,onChat:()->Unit) {
    BackHandler {vm.closeGuide()}
    val progress=OnboardingProgress(state.configs.size,state.roles.size,state.inbox.size,state.completedReplies)
    val context=LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start=8.dp,end=16.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={vm.closeGuide()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
            Column(Modifier.weight(1f)) {Text("新手引导与帮助",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text("按真实进度完成第一次聊天",style=MaterialTheme.typography.bodySmall)}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(if(progress.done)"已经可以开始日常聊天"else"完成 ${progress.completedSteps}/4 步",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                LinearProgressIndicator(progress={progress.completedSteps/4f},modifier=Modifier.fillMaxWidth())
                Text("API Key 只在模型连接页填写；引导和诊断不会显示或复制 Key、服务地址、聊天正文、人设和本机路径。",style=MaterialTheme.typography.bodySmall)
            }}
            GuideStep(1,"配置模型",if(progress.configured)"已保存 ${state.configs.size} 个连接"else"添加 HTTPS 地址、模型和自己的 API Key",progress.configured,Icons.Default.Hub,"打开模型连接",onConnections)
            GuideStep(2,"选择角色",if(progress.roleReady)"已有 ${state.roles.size} 个角色，可编辑人设和开场白"else"创建一个角色",progress.roleReady,Icons.Default.Person,"查看角色",onRoles)
            GuideStep(3,"开启对话",if(progress.conversationReady)"已有 ${state.inbox.size} 段对话"else"选择角色和模型，新建一段独立对话",progress.conversationReady,Icons.Default.Forum,"前往聊天",onChat)
            GuideStep(4,"收到首条回复",if(progress.firstReplyReady)"已收到完整回复"else"发送一句问候；可停止、重试并保留部分答案",progress.firstReplyReady,Icons.AutoMirrored.Filled.Send,"打开聊天",onChat)

            HorizontalDivider()
            Text("遇到问题",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text("Key 或地址错误会引导到模型连接；网络、限流、存储和工具问题会打开本页。错误信息只使用固定类别，不显示服务端正文。",style=MaterialTheme.typography.bodyMedium)
            state.diagnostic?.let {report->
                Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("脱敏诊断预览",style=MaterialTheme.typography.titleMedium)
                    Text(report.render(),style=MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick={(context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("星语脱敏诊断",report.render()))}){Icon(Icons.Default.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("复制这份预览")}
                }}
            }?:OutlinedButton(onClick={vm.previewDiagnostic()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.HealthAndSafety,null);Spacer(Modifier.width(8.dp));Text("生成当前脱敏诊断")}

            Text("常用入口",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            HelpItem("聊天外观","进入聊天后使用调色盘选择本机背景；图片不发给模型。")
            HelpItem("发送图片","先在模型连接中确认模型支持视觉并开启图片输入，再点聊天输入栏的加号选择图片。消息图片会压缩后加密保存，并发送到你确认的模型地址；背景和头像仍不会发送。")
            HelpItem("文件与链接","聊天输入栏的加号可读取本机 TXT/Markdown 或 HTTPS 链接。应用只接受受限 UTF-8 文本，链接不携带凭据、不执行脚本、不跟随重定向；完整预览确认后才加入下一条消息。")
            HelpItem("TXT/Markdown 伴读","聊天加号可打开当前角色的加密书架。导入文件不会自动上传；只有在阅读器点击“引用当前片段”的一页会进入待发送消息，并保留章节和字符位置。")
            HelpItem("保存 AI 回复","点击已完成的 AI 消息，选择“保存为 TXT / Markdown”。应用先显示完整预览、真实 UTF-8 大小和 MIME，再通过系统文件选择器保存并回读核对。")
            HelpItem("角色与设定","角色资料管理用户面具、世界书、关系、长期记忆、原味模型和工具权限。公开角色模板只含名称、人设和开场白。")
            HelpItem("长期记忆","可以手工保存，或点击一条完整 AI 回复选择“提炼为记忆候选”。提炼前核对发送内容和模型，返回后逐条修改、勾选并确认；虚构剧情会明确标记。")
            HelpItem("日记与心事簿","角色资料页可手工记录，或填写明确主题后让所选模型生成待确认草稿。普通聊天不会自动读取；导出前会显示完整 Markdown 和来源说明。")
            HelpItem("时间与天气","角色资料页可分别开启本机时间和已保存天气。城市文字与候选坐标会分两次确认后发往固定 Open-Meteo 服务；应用不请求定位、不猜城市、不后台刷新。")
            HelpItem("日程与记录","角色资料页可管理日程、待办、纪念日和经期。每条聊天分享默认关闭；提醒只显示泛化通知，可能受系统节电和通知权限影响。")
            HelpItem("隐私与备份","设置中可启用系统身份验证；个人备份使用独立口令，可选包含 API Key。卸载或清空应用会删除未备份数据。")
            HelpItem("工具扩展","扩展安装后默认停用，调用前逐次确认。")
            HelpItem("当前边界","本版的记忆检索只在本机按文字相似度计算，日记不会自动进入聊天，天气只使用六小时内的手工快照；经期只记录用户填写内容，不做健康预测。主动消息默认关闭，只在用户明确授权的角色、频率、免打扰范围内运行；后台自动提炼、语音通话、真实支付或云同步仍按总计划后续实现。")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick={vm.closeGuide()},modifier=Modifier.weight(1f)){Text(if(state.guidePending)"暂时收起"else"关闭")}
                if(state.guidePending)Button(onClick={vm.closeGuide(true)},modifier=Modifier.weight(1f)){Text(if(progress.done)"完成引导"else"不再提示")}
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun GuideStep(number:Int,title:String,caption:String,complete:Boolean,icon:androidx.compose.ui.graphics.vector.ImageVector,action:String,onClick:()->Unit) {
    Surface(shape=RoundedCornerShape(18.dp),tonalElevation=1.dp) {Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Surface(shape=RoundedCornerShape(14.dp),color=if(complete)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){if(complete)Icon(Icons.Default.CheckCircle,"已完成",tint=MaterialTheme.colorScheme.primary)else Icon(icon,null)}
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text("$number. $title",fontWeight=FontWeight.SemiBold);Text(caption,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick=onClick,contentPadding=PaddingValues(0.dp)){Text(action)}}
    }}
}

@Composable private fun HelpItem(title:String,text:String) {
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontWeight=FontWeight.SemiBold);Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
