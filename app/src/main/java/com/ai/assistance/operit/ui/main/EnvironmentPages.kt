package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.api.environment.OpenMeteoService
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable private fun EnvironmentSurface(title:String,subtitle:String,onBack:()->Unit,error:String?=null,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onBack,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
                Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(subtitle,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
            }
            if(error!=null)Text(error,Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            content()
        }}
    }
}

@Composable internal fun EnvironmentDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val scope=role.scope();val data by remember(scope){vm.environment(scope)}.collectAsStateWithLifecycle<EnvironmentData?>(null)
    var city by remember(scope){mutableStateOf("")}
    var deleting by remember(scope){mutableStateOf(false)}
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();delay(1000)}}
    EnvironmentSurface("时间与天气",role.name,{if(!state.working)onDismiss()},state.environmentError) {
        val loaded=data
        when {
            loaded==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            loaded.error!=null->Text(loaded.error,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
            else->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                val profile=loaded.profile
                Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Schedule,null);Spacer(Modifier.width(10.dp));Text("本机确定时间",fontWeight=FontWeight.SemiBold)}
                    Text(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX '['VV']'")))
                    Text("时间取自手机时钟和当前时区，不需要联网。",style=MaterialTheme.typography.bodySmall)
                }}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text("聊天感知本机时间",fontWeight=FontWeight.SemiBold);Text("开启后，从下一条消息起冻结并加入当时的时间。原味模式始终不加入。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(profile?.timeContextEnabled==true,{vm.setEnvironmentToggles(scope,it,profile?.weatherContextEnabled==true)},enabled=!state.working)
                }
                HorizontalDivider()
                Text("天气城市",style=MaterialTheme.typography.titleMedium)
                Text("应用不会申请定位、按 IP 猜城市或后台刷新。城市文字只有在下一步再次确认后才会发送给 Open-Meteo。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(city,{if(it.length<=Environments.CITY_LIMIT)city=it},Modifier.fillMaxWidth(),label={Text("城市，可附加省份或国家")},singleLine=true,enabled=!state.working,supportingText={Text("例如：杭州，中国 · ${city.length}/${Environments.CITY_LIMIT}")})
                Button(onClick={vm.previewWeatherSearch(scope,role.name,city.trim())},enabled=!state.working&&runCatching {Environments.validateCity(city.trim())}.isSuccess,modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.LocationCity,null);Spacer(Modifier.width(8.dp));Text("预览城市检索")}
                profile?.candidate()?.let {candidate->
                    val snapshot=profile.snapshot()
                    Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Cloud,null);Spacer(Modifier.width(8.dp));Text(candidate.label(),fontWeight=FontWeight.SemiBold)}
                        Text("${candidate.timezone} · Open-Meteo / GeoNames",style=MaterialTheme.typography.bodySmall)
                        if(snapshot==null)Text("尚未保存天气快照。") else {
                            Text("${WeatherCodes.label(snapshot.weatherCode)} · ${Environments.format(snapshot.temperature)}°C · 体感 ${Environments.format(snapshot.apparentTemperature)}°C")
                            Text("湿度 ${snapshot.humidity}% · 降水 ${Environments.format(snapshot.precipitation)} mm · 风速 ${Environments.format(snapshot.windSpeed)} km/h")
                            Text("今日 ${Environments.format(snapshot.todayMin)}–${Environments.format(snapshot.todayMax)}°C · 最高降水概率 ${snapshot.todayPrecipitationProbability}%")
                            Text("服务时间 ${snapshot.weatherTime} · 获取于 ${Instant.ofEpochMilli(snapshot.fetchedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}",style=MaterialTheme.typography.bodySmall)
                        }
                        Text("天气数据可能不准确，请勿用于生命安全或重大决策。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick={vm.previewWeatherRefresh(scope,role.name,profile)},enabled=!state.working&&snapshot!=null){Text("手工刷新")}
                            TextButton(onClick={deleting=true},enabled=!state.working){Icon(Icons.Default.DeleteOutline,null);Text("清除城市")}
                        }
                    }}
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){Text("聊天感知已保存天气",fontWeight=FontWeight.SemiBold);Text("仅加入最近六小时内的完整快照；过期后自动排除，绝不自动联网刷新。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    Switch(profile?.weatherContextEnabled==true,{vm.setEnvironmentToggles(scope,profile?.timeContextEnabled==true,it)},enabled=!state.working&&profile?.snapshot()!=null)
                }
                Text("地点与天气保存在当前角色的加密空间。复制角色和公开模板不携带；个人加密备份会包含。免费 Open-Meteo 服务仅用于非商业开发与个人测试，数据许可 CC BY 4.0。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
            }
        }
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("清除天气城市？")},text={Text("将删除当前角色保存的城市和天气快照，并关闭聊天天气感知。本机时间开关保持不变。")},
        confirmButton={TextButton(onClick={deleting=false;vm.clearWeather(scope)}){Text("清除")}},dismissButton={TextButton(onClick={deleting=false}){Text("取消")}})
}

@Composable internal fun WeatherLookupDialog(state:CompanionState,vm:CompanionViewModel) {
    val ui=state.weatherLookup?:return
    AlertDialog(onDismissRequest={if(!state.working)vm.dismissWeatherLookup()},icon={Icon(Icons.Default.Cloud,null)},title={Text(when(ui.stage){"SEARCH_CONFIRM"->"确认城市检索";"SEARCHING"->"正在检索城市";"CANDIDATES"->"选择准确城市";"FORECAST_CONFIRM"->"确认天气地点";else->"正在获取天气"})},
        text={Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            when(ui.stage) {
                "SEARCH_CONFIRM"->{Text("将发送的完整城市文字",fontWeight=FontWeight.SemiBold);SelectionContainer{Text(ui.city,Modifier.fillMaxWidth().padding(12.dp))};Text("固定服务：${OpenMeteoService.GEOCODING}");Text("不发送角色、聊天、API Key、设备定位或其它本机资料。地点数据来自 GeoNames。",style=MaterialTheme.typography.bodySmall)}
                "SEARCHING"->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator();Text("只向固定 Open-Meteo 地点接口发送已确认的城市文字…")}
                "CANDIDATES"->{Text("同名城市可能位于不同地区。请选择一项；此时尚未请求天气。",style=MaterialTheme.typography.bodySmall);ui.candidates.forEach {candidate->OutlinedButton(onClick={vm.selectWeatherCandidate(candidate)},modifier=Modifier.fillMaxWidth()){Column(Modifier.fillMaxWidth()){Text(candidate.label());Text(candidate.timezone,style=MaterialTheme.typography.bodySmall)}}}}
                "FORECAST_CONFIRM"->{val candidate=requireNotNull(ui.selected);Text(candidate.label(),fontWeight=FontWeight.SemiBold);Text("时区：${candidate.timezone}");SelectionContainer{Text("坐标：${candidate.latitude}, ${candidate.longitude}")};Text("固定服务：${OpenMeteoService.FORECAST}");Text("确认后只发送以上坐标、时区和固定天气变量。服务可能记录请求 IP；天气与地点数据采用 CC BY 4.0，免费接口只适用于非商业用途。",style=MaterialTheme.typography.bodySmall)}
                else->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator();Text("正在获取当前天气和三日预报，不会自动重定向…")}
            }
            state.environmentError?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        }},
        confirmButton={when(ui.stage){
            "SEARCH_CONFIRM"->TextButton(onClick=vm::searchWeather,enabled=!state.working){Text("确认并检索")}
            "FORECAST_CONFIRM"->TextButton(onClick=vm::fetchWeather,enabled=!state.working){Text("确认并获取")}
            "SEARCHING","FETCHING"->TextButton(onClick=vm::cancelWeatherLookup){Text("取消请求")}
            else->TextButton(onClick=vm::dismissWeatherLookup,enabled=!state.working){Text("取消")}
        }},dismissButton={if(ui.stage in setOf("SEARCH_CONFIRM","FORECAST_CONFIRM"))TextButton(onClick=vm::dismissWeatherLookup,enabled=!state.working){Text("返回")}})
}
