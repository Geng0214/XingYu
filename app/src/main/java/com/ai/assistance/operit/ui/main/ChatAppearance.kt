package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.RoleEntity

internal data class ChatPalette(val background: Color,val outgoing: Color,val incoming: Color,val ink: Color,val ornament: Color)
internal val LocalCompanionChatPalette=staticCompositionLocalOf { "rose" }
internal class LocalImagePicker : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context,input: Array<String>): Intent = super.createIntent(context,input).putExtra(Intent.EXTRA_LOCAL_ONLY,true)
}
internal fun chatPalette(name: String,dark: Boolean): ChatPalette {
    if(dark)return when(name) {
        "green"->ChatPalette(Color(0xFF192326),Color(0xFF29483E),Color(0xFF2C333A),Color(0xFFF0F3F5),Color(0xFF668079))
        "blue"->ChatPalette(Color(0xFF1A232E),Color(0xFF29435F),Color(0xFF2B3440),Color(0xFFF0F3F5),Color(0xFF6786A3))
        "paper"->ChatPalette(Color(0xFF211D19),Color(0xFF654936),Color(0xFF393027),Color(0xFFF0E8DD),Color(0xFFAD8F79))
        "sea"->ChatPalette(Color(0xFF142327),Color(0xFF28585A),Color(0xFF26383D),Color(0xFFDCEBED),Color(0xFF75AEB0))
        "midnight"->ChatPalette(Color(0xFF101A2A),Color(0xFF274B57),Color(0xFF263447),Color(0xFFE7EDF4),Color(0xFF87C5CD))
        "forest"->ChatPalette(Color(0xFF152119),Color(0xFF30523A),Color(0xFF26372A),Color(0xFFE7EEE2),Color(0xFF8DAA80))
        "sakura"->ChatPalette(Color(0xFF261B25),Color(0xFF63364D),Color(0xFF3B2A39),Color(0xFFF2E7EF),Color(0xFFCF9DBD))
        "observatory"->ChatPalette(Color(0xFF0E2130),Color(0xFF534329),Color(0xFF253D4B),Color(0xFFEDF4F7),Color(0xFFE8C37A))
        else->ChatPalette(Color(0xFF261F27),Color(0xFF523745),Color(0xFF383039),Color(0xFFF8F0F4),Color(0xFFAA8197))
    }
    return when(name) {
        "green"->ChatPalette(Color(0xFFEAF0E8),Color(0xFFD2EDC6),Color.White,Color(0xFF1C2922),Color(0xFF719982))
        "blue"->ChatPalette(Color(0xFFE9EFF5),Color(0xFFD5E9FC),Color.White,Color(0xFF243342),Color(0xFF7D9AB5))
        "paper"->ChatPalette(Color(0xFFFAF6EE),Color(0xFFEEDBC9),Color.White,Color(0xFF2B241E),Color(0xFFAA8068))
        "sea"->ChatPalette(Color(0xFFF2F8F7),Color(0xFFC9E9E7),Color.White,Color(0xFF203337),Color(0xFF72A6A7))
        "midnight"->ChatPalette(Color(0xFFF3F6FA),Color(0xFFD1E9EF),Color.White,Color(0xFF242D3B),Color(0xFF7698AE))
        "forest"->ChatPalette(Color(0xFFF5F8F2),Color(0xFFD3E9CE),Color(0xFFFEFFF9),Color(0xFF273B2B),Color(0xFF83A07A))
        "sakura"->ChatPalette(Color(0xFFFFF6FA),Color(0xFFF4D2E4),Color(0xFFFFFCFE),Color(0xFF362C3C),Color(0xFFB980A1))
        "observatory"->ChatPalette(Color(0xFFF3F7F9),Color(0xFFF3E4BD),Color.White,Color(0xFF243641),Color(0xFFAB8A4A))
        else->ChatPalette(Color(0xFFF0E8EC),Color(0xFFF6DDE7),Color.White,Color(0xFF352D36),Color(0xFFB990A4))
    }
}

@Composable internal fun ChatBackdrop(bitmap: Bitmap?,dimming: Float,palette: ChatPalette) {
    if(bitmap!=null) {
        Image(bitmap.asImageBitmap(),contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background((if(companionIsDark())Color.Black else Color.White).copy(alpha=dimming)))
    // Keep the no-wallpaper state quiet like the selected reference; user-picked images remain untouched.
    } else Box(Modifier.fillMaxSize().background(palette.background))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ChatAppearanceDialog(state: CompanionState,vm: CompanionViewModel,role: RoleEntity,onPick:()->Unit,onDismiss:()->Unit) {
    var palette by remember { mutableStateOf(state.appearance?.palette ?: "rose") }
    var dimming by remember { mutableFloatStateOf(state.appearance?.dimming ?: 0.18f) }
    val colors=chatPalette(palette,companionIsDark())
    AlertDialog(onDismissRequest=onDismiss,title={Text("${role.name} · 聊天外观")},text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(18.dp))) {
            ChatBackdrop(state.wallpaper,dimming,colors)
            PreviewBubbles(colors)
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("rose" to "柔粉","green" to "清绿","blue" to "雾蓝").forEach { (id,label)->FilterChip(palette==id,{palette=id},label={Text(label)}) }
        }
        OutlinedButton(enabled=!state.working,onClick=onPick,modifier=Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Image,null);Spacer(Modifier.width(8.dp));Text("从本机选择背景图片")
        }
        if(state.wallpaper!=null) {
            Text("背景柔化",style=MaterialTheme.typography.labelLarge)
            Slider(dimming,{dimming=it},valueRange=0f..0.65f)
            TextButton(enabled=!state.working,onClick={vm.setAppearance(role.scope(),dimming,palette,true);onDismiss()}){Text("移除自选背景")}
        }
        Text("只应用于此角色。图片不发送给模型，也不包含在角色模板中。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }},confirmButton={TextButton(enabled=!state.working,onClick={vm.setAppearance(role.scope(),dimming,palette);onDismiss()}){Text("保存外观")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable internal fun WallpaperPreviewDialog(state: CompanionState,vm: CompanionViewModel) {
    val preview=state.wallpaperPreview ?: return
    var dimming by remember(preview) { mutableFloatStateOf(0.18f) }
    val palette=chatPalette("rose",companionIsDark())
    AlertDialog(onDismissRequest={vm.dismissWallpaper()},title={Text("${preview.roleName} · 背景预览")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().heightIn(max=360.dp).aspectRatio(0.8f).clip(RoundedCornerShape(18.dp))) {
            ChatBackdrop(preview.image.bitmap,dimming,palette)
            PreviewBubbles(palette)
        }
        Text("背景柔化",style=MaterialTheme.typography.labelLarge)
        Slider(dimming,{dimming=it},valueRange=0f..0.65f)
        Text("图片会居中铺满聊天区域；仅保存处理后的画面，不保留位置等照片信息。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=!state.working,onClick={vm.saveWallpaper(dimming)}){Text("使用这张背景")}},dismissButton={TextButton(onClick={vm.dismissWallpaper()}){Text("取消")}})
}

@Composable private fun BoxScope.PreviewBubbles(palette: ChatPalette) {
    Column(Modifier.fillMaxWidth().align(Alignment.Center).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Surface(shape=themedMessageShape(false),color=palette.incoming){Text("今天有什么想聊的？",Modifier.padding(12.dp),color=palette.ink)}
        Surface(shape=themedMessageShape(true),color=palette.outgoing,modifier=Modifier.align(Alignment.End)){Text("想和你说说今天。",Modifier.padding(12.dp),color=palette.ink)}
    }
}
