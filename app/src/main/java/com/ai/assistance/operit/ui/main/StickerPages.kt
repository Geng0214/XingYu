package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.RoleEntity
import com.ai.assistance.operit.data.model.StickerAsset
import com.ai.assistance.operit.data.model.StickerAssets

@Composable internal fun StickerPreviewDialog(state:CompanionState,vm:CompanionViewModel) {
    val preview=state.stickerPreview?:return
    var name by remember(preview.image.sha256){mutableStateOf(preview.suggestedName)}
    val valid=remember(name){runCatching {StickerAssets.validateName(name.trim());true}.getOrDefault(false)}
    AlertDialog(onDismissRequest=vm::dismissStickerPreview,title={Text("保存到 ${preview.roleName} 的素材库")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Image(preview.image.bitmap.asImageBitmap(),"素材预览",Modifier.fillMaxWidth().heightIn(max=260.dp).clip(RoundedCornerShape(20.dp)),contentScale=ContentScale.Fit)
            OutlinedTextField(name,{if(it.length<=StickerAssets.MAX_NAME)name=it},Modifier.fillMaxWidth(),label={Text("表情名称")},supportingText={Text("模型未开启视觉时，只会收到这个名称。")},singleLine=true)
            Text("只保存重编码后的静态 JPEG 像素，不保留原文件名、位置或 EXIF。GIF 只取首帧；导入外部素材前请确认你有权使用。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },confirmButton={TextButton(enabled=valid&&!state.working,onClick={vm.saveSticker(name.trim())}){Text("保存素材")}},dismissButton={TextButton(onClick=vm::dismissStickerPreview){Text("取消")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun StickerLibraryDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onImport:()->Unit,onEmoji:(String)->Unit,onDismiss:()->Unit) {
    var favoritesOnly by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<StickerAsset?>(null)}
    val assets=state.stickerAssets.filter {!favoritesOnly||it.favorite}
    AlertDialog(onDismissRequest=onDismiss,title={Text("表情与本机素材")},text={
        Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text("仅显示 ${role.name} 的空间",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
            Text("常用表情",style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                StickerAssets.EMOJI.forEach {emoji->Surface(shape=RoundedCornerShape(13.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.clickable {onEmoji(emoji);onDismiss()}) {Text(emoji,Modifier.padding(horizontal=11.dp,vertical=8.dp),style=MaterialTheme.typography.titleLarge)}}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text("本机素材",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                FilterChip(favoritesOnly,{favoritesOnly=!favoritesOnly},label={Text("只看收藏")},leadingIcon={Icon(Icons.Default.Star,null,Modifier.size(16.dp))})
            }
            if(assets.isEmpty())Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                Text(if(favoritesOnly)"还没有收藏素材。"else"还没有本机素材，导入后可重复发送。",Modifier.fillMaxWidth().padding(18.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                assets.forEach {asset->StickerAssetCard(asset,state.working,onChoose={vm.chooseSticker(role.scope(),asset.id);onDismiss()},onFavorite={vm.setStickerFavorite(role.scope(),asset.id,!asset.favorite)},onDelete={deleting=asset})}
            }
            OutlinedButton(enabled=!state.working&&state.stickerAssets.size<StickerAssets.MAX_PER_ROLE,onClick=onImport,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.AddPhotoAlternate,null);Spacer(Modifier.width(8.dp));Text("导入本机图片")}
            Text("当前支持静态 JPEG、PNG、WebP，GIF 仅保存首帧。没有联网表情商店、外部素材下载或 meme 模板；素材名称会随消息发给模型，像素只在当前连接开启图片输入时发送。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("完成")}})
    deleting?.let {asset->AlertDialog(onDismissRequest={deleting=null},title={Text("删除“${asset.displayName}”？")},text={Text("素材会从 ${role.name} 的库中删除。已经发送的消息保留独立快照，不会被删除。")},
        confirmButton={TextButton(enabled=!state.working,onClick={vm.deleteSticker(role.scope(),asset.id);deleting=null}){Text("删除",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
}

@Composable private fun StickerAssetCard(asset:StickerAsset,working:Boolean,onChoose:()->Unit,onFavorite:()->Unit,onDelete:()->Unit) {
    val bitmap=remember(asset.sha256){BitmapFactory.decodeByteArray(asset.data,0,asset.data.size)}
    Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.width(116.dp)) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            if(bitmap!=null)Image(bitmap.asImageBitmap(),asset.displayName,Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(topStart=18.dp,topEnd=18.dp)).clickable(enabled=!working,onClick=onChoose),contentScale=ContentScale.Crop)
            else Box(Modifier.fillMaxWidth().height(86.dp),contentAlignment=Alignment.Center){Icon(Icons.Default.BrokenImage,"素材无法显示")}
            Text(asset.displayName,Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=5.dp),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                IconButton(enabled=!working,onClick=onFavorite,modifier=Modifier.size(36.dp)){Icon(if(asset.favorite)Icons.Default.Star else Icons.Default.StarBorder,if(asset.favorite)"取消收藏"else"收藏",Modifier.size(19.dp),tint=if(asset.favorite)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)}
                IconButton(enabled=!working,onClick=onDelete,modifier=Modifier.size(36.dp)){Icon(Icons.Default.DeleteOutline,"删除素材",Modifier.size(19.dp),tint=MaterialTheme.colorScheme.error)}
            }
        }
    }
}
