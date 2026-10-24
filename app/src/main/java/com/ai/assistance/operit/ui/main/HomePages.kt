package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon
import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

internal fun homeTime(time:Long)=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ColumnScope.HomePage(role:RoleEntity?,state:CompanionState,vm:CompanionViewModel,onClose:()->Unit,initialCommandId:String?=null,isHomeTab:Boolean=false) {
    var token by remember {mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val activity=remember(context){generateSequence(context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().first()}
    DisposableEffect(Unit) {
        val opened=vm.openHome();token=opened
        onDispose {if(!activity.isChangingConfigurations)vm.closeHome(opened)}
    }
    val ui=state.home?.takeIf {it.token==token}
    val home=remember(ui?.view?.home?.stateJson){ui?.view?.home?.value()}
    val residents=state.roles.filter {home?.residents?.get(it.id)?.role==it.scope()}
    var selectedId by rememberSaveable {mutableStateOf(role?.id)}
    LaunchedEffect(home?.residents?.keys,residents.map {it.id}) {
        if(residents.none {it.id==selectedId})selectedId=residents.firstOrNull()?.id
    }
    val selectedRole=residents.firstOrNull {it.id==selectedId}
    var yard by rememberSaveable(home?.scope?.homeId){mutableStateOf(false)}
    var sceneMounted by remember(token){mutableStateOf(false)}
    val clockTick by produceState(System.currentTimeMillis()){while(true){value=System.currentTimeMillis();delay(1000)}}
    val now=remember(clockTick,home?.updatedAt){System.currentTimeMillis()}
    BackHandler(enabled=!isHomeTab,onBack=onClose)
    BackHandler(enabled=yard,onBack={yard=false})
    val enabled=ui!=null&&home!=null&&!state.working&&!ui.positionSaving&&ui.pending==null&&homeClockValid(home,now)
    Column(Modifier.weight(1f).fillMaxWidth()) {
        TopAppBar(title={Text(home?.name?:"家园",maxLines=1,overflow=TextOverflow.Ellipsis)},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=CompanionSceneColors.wallBottom,
                titleContentColor=CompanionSceneColors.moonlight,navigationIconContentColor=CompanionSceneColors.moonlight),
            navigationIcon={if(!isHomeTab)IconButton(onClick=onClose){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}})
        when {
            ui==null||ui.loading->LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.readError!=null->Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(ui.readError,color=MaterialTheme.colorScheme.error)
                Button(onClick={vm.refreshHome(ui.token)}){Text("重新读取")}
            }
            home==null->LinearProgressIndicator(Modifier.fillMaxWidth())
            yard&&!sceneMounted&&ui.positionSaving->LinearProgressIndicator(Modifier.fillMaxWidth())
            else->{
                if(yard) {
                    DisposableEffect(ui.token){sceneMounted=true;onDispose {sceneMounted=false}}
                    HomeGameUi(home,selectedRole,residents,vm,ui,now,enabled,state.working,initialCommandId,
                        onResident={selectedId=it},onAction={vm.homeAction(ui.token,it)},onCompanion={yard=false})
                }else {
                    CompanionHomeUi(home,selectedRole,residents,vm,ui,now,enabled,initialCommandId,
                        onRole={selectedId=it},onOpenYard={yard=true})
                }
            }
        }
    }
}

@Composable internal fun HomeRecord(command:HomeCommandRow,focused:Boolean=false) {
    Column(Modifier.fillMaxWidth().then(if(focused)Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha=.38f),RoundedCornerShape(12.dp)).padding(12.dp)else Modifier),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(command.description,style=MaterialTheme.typography.bodyMedium)
        Text(homeTime(command.createdAt),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(top=8.dp))
    }
}
@Composable internal fun HomeHistoryControl(ui:HomeUi,working:Boolean,vm:CompanionViewModel) {
    if(ui.history==null)TextButton(enabled=!working,onClick={vm.homeHistory(ui.token)}){Text("完整回忆")}
}
