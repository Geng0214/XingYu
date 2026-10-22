package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

private data class PortraitImage(val bitmap: Bitmap?=null,val error: Boolean=false)
@Composable internal fun PortraitAvatar(name: String,scope: RoleAccessScope,vm: CompanionViewModel,size: Dp=48.dp,kind: String="role") {
    key(scope,kind) {
        val image by produceState(PortraitImage(),vm) {
            try { vm.portrait(scope,kind).collect { value=PortraitImage(it) } }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { value=PortraitImage(error=true) }
        }
        if(image.error) Icon(Icons.Default.BrokenImage,"头像无法读取",Modifier.size(size))
        else if(image.bitmap!=null) Image(image.bitmap!!.asImageBitmap(),null,Modifier.size(size).clip(RoundedCornerShape(size*0.32f)),contentScale=ContentScale.Crop)
        else RoleAvatar(name,if(kind=="self")"self"else scope.roleId,size)
    }
}

@Composable internal fun PortraitDialog(state: CompanionState,vm: CompanionViewModel,role: RoleEntity,onPick:(String)->Unit,onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("聊天头像")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("头像只用于与${role.name}的聊天，不发送给模型，也不随角色模板分享。",style=MaterialTheme.typography.bodyMedium)
        listOf("role" to role.name,"self" to role.selfName()).forEach { (kind,name)->
            Row(verticalAlignment=Alignment.CenterVertically) {
                PortraitAvatar(name,role.scope(),vm,48.dp,kind)
                Text(name,Modifier.weight(1f).padding(horizontal=12.dp))
                TextButton(enabled=!state.working,onClick={onPick(kind)}){Text("选择")}
                IconButton(enabled=!state.working,onClick={vm.resetPortrait(role.scope(),kind)}){Icon(Icons.Default.RestartAlt,"重置${name}的头像")}
            }
        }
        Text("使用本机图片，居中裁成正方形。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick=onDismiss){Text("完成")}})
}

@Composable internal fun PortraitPreviewDialog(state: CompanionState,vm: CompanionViewModel) {
    state.portraitPreview?.let { preview->
        AlertDialog(onDismissRequest=vm::dismissPortrait,title={Text("预览头像")},text={Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp),modifier=Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Image(preview.image.bitmap.asImageBitmap(),"选中的头像",Modifier.size(128.dp).clip(RoundedCornerShape(32.dp)))
            Text(if(preview.kind=="self")"我 · 与${preview.roleName}的聊天"else preview.roleName)
        }},confirmButton={TextButton(enabled=!state.working,onClick=vm::savePortrait){Text("使用头像")}},dismissButton={TextButton(onClick=vm::dismissPortrait){Text("取消")}})
    }
}

private data class SearchPage(val messages: List<MessageEntity> = emptyList(),val loading: Boolean=true,val error: Boolean=false)
@Composable internal fun MessageLibrary(role: RoleEntity,vm: CompanionViewModel,onDismiss:()->Unit) {
    var query by remember(role.scope()) { mutableStateOf("") }
    var favorites by remember(role.scope()) { mutableStateOf(false) }
    var page by remember(role.scope()) { mutableIntStateOf(0) }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) { Column(Modifier.safeDrawingPadding().imePadding()) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onDismiss){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回聊天")}
                Column(Modifier.weight(1f)) { Text("查找聊天内容",style=MaterialTheme.typography.titleLarge);Text("仅限 ${role.name} 的空间",style=MaterialTheme.typography.bodySmall) }
            }
            OutlinedTextField(query,{if(it.length<=200){query=it;page=0}},Modifier.fillMaxWidth().padding(horizontal=16.dp),singleLine=true,label={Text("搜索历史正文")},leadingIcon={Icon(Icons.Default.Search,null)})
            Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(!favorites,{favorites=false;page=0},label={Text("全部消息")})
                FilterChip(favorites,{favorites=true;page=0},label={Text("我的收藏")},leadingIcon={Icon(Icons.Default.Star,null,Modifier.size(18.dp))})
            }
            // A new composition key clears rows immediately, before a cancelled query can finish.
            key(role.scope(),query,favorites,page) {
                val result by produceState(SearchPage(),vm) {
                    try { delay(250);vm.search(role.scope(),query,favorites,page).collect { value=SearchPage(it,false) } }
                    catch(e:CancellationException) { throw e }
                    catch(_:Exception) { value=SearchPage(loading=false,error=true) }
                }
                if(result.loading) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                else if(result.error) Box(Modifier.weight(1f).padding(24.dp)){Text("无法读取此角色的消息，请返回后重新打开。")}
                else LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(result.messages.isEmpty())item { Text(if(favorites)"还没有符合条件的收藏。"else"没有找到符合条件的消息。",Modifier.padding(vertical=24.dp)) }
                    items(result.messages,key={it.id}) { message->
                        Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().clickable {
                            vm.selectChat(role.scope(),message.chatId,message.id);onDismiss()
                        }) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text((if(message.kind=="user")"我"else role.name)+" · "+java.text.SimpleDateFormat("MM-dd HH:mm",java.util.Locale.CHINA).format(java.util.Date(message.createdAt)),style=MaterialTheme.typography.labelMedium)
                            Text(message.content,maxLines=5,overflow=TextOverflow.Ellipsis)
                            if(message.favorite) Text("已收藏 · 点击查看原文",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                            else Text("点击定位到原消息",style=MaterialTheme.typography.labelSmall)
                        } }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=page>0&&!result.loading,onClick={page--}){Text("上一页")}
                    Text("第 ${page+1} 页",style=MaterialTheme.typography.bodySmall)
                    TextButton(enabled=result.messages.size==50&&!result.loading&&page<10000,onClick={page++}){Text("下一页")}
                }
            }
        } }
    }
}
