package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val plannerDayFormat = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
private val plannerTimeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable internal fun PlannerBoard(
    loaded: PlannerData, tab: Int, selectedDate: LocalDate, today: LocalDate, zone: ZoneId,
    listView: Boolean, monthExpanded: Boolean, working: Boolean,
    onDate: (LocalDate) -> Unit, onExpand: () -> Unit, onList: () -> Unit, onAdd: () -> Unit,
    onOpen: (PlannerEntry) -> Unit, onEdit: (PlannerEntry) -> Unit, onDelete: (PlannerEntry) -> Unit,
    onCompleted: (PlannerEntry, Boolean) -> Unit,
    onEditCycle: (CycleRecord) -> Unit, onDeleteCycle: (CycleRecord) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val kind = listOf("event", "task", "anniversary")[tab.coerceAtMost(2)]
        val entries = remember(loaded.entries, kind) { loaded.entries.filter { it.kind == kind } }
        val wide = maxWidth >= 840.dp && LocalDensity.current.fontScale < 1.5f && tab != 3 && !listView
        val calendar: @Composable () -> Unit = {
            PlannerCalendarPane(selectedDate, today, entries, zone, wide || monthExpanded, !wide, onDate, onExpand)
        }
        val agenda: @Composable () -> Unit = {
            PlannerAgenda(entries, loaded.cycles, tab, selectedDate, today, zone, listView, working,
                onList, onAdd, onOpen, onEdit, onDelete, onCompleted, onEditCycle, onDeleteCycle)
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.Center) {
                Row(Modifier.widthIn(max = 1100.dp).fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column(Modifier.width(360.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) { calendar() }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 88.dp)) { agenda() }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 88.dp)) {
                if (tab != 3 && !listView) calendar()
                agenda()
            }
        }
    }
}

@Composable private fun PlannerCalendarPane(
    selectedDate: LocalDate, today: LocalDate, entries: List<PlannerEntry>, zone: ZoneId,
    expanded: Boolean, canCollapse: Boolean, onDate: (LocalDate) -> Unit, onExpand: () -> Unit
) {
    var datePickerOpen by remember { mutableStateOf(false) }
    val month = YearMonth.from(selectedDate)
    val days = remember(selectedDate, expanded) {
        if (expanded) PlannerCalendar.monthDays(month) else PlannerCalendar.weekDays(selectedDate)
    }
    val markers = remember(entries, days, zone) { days.associateWith { PlannerCalendar.onDay(entries, it, zone).size } }
    Column(Modifier.fillMaxWidth().animateContentSize().padding(top = 8.dp, bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { datePickerOpen = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
            }
            TextButton(onClick = { onDate(today) }) { Text("今天") }
            IconButton(onClick = {
                onDate(if (expanded) selectedDate.minusMonths(1) else selectedDate.minusWeeks(1))
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (expanded) "上个月" else "上一周") }
            IconButton(onClick = {
                onDate(if (expanded) selectedDate.plusMonths(1) else selectedDate.plusWeeks(1))
            }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, if (expanded) "下个月" else "下一周") }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        days.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val isSelected = date == selectedDate
                    val isToday = date == today
                    val count = markers.getValue(date)
                    Surface(
                        onClick = { onDate(date) }, modifier = Modifier.weight(1f).padding(vertical = 2.dp).semantics {
                            contentDescription = "${date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA))}${if (isToday) "，今天" else ""}，${count}项安排"
                            selected = isSelected
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = when {
                            isSelected -> MaterialTheme.colorScheme.onPrimary
                            YearMonth.from(date) != month -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        border = if (isToday && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Column(Modifier.heightIn(min = 56.dp).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(date.dayOfMonth.toString(), Modifier.clearAndSetSemantics {}, style = MaterialTheme.typography.titleMedium, fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal)
                            Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                                repeat(minOf(count, 3)) {
                                    Surface(Modifier.size(3.dp), shape = CircleShape, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) {}
                                }
                            }
                        }
                    }
                }
            }
        }
        if (canCollapse) TextButton(onClick = onExpand, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (expanded) "收起月历" else "展开月历")
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, Modifier.size(20.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
    if (datePickerOpen) PlannerDatePicker(selectedDate, { datePickerOpen = false }) { onDate(it); datePickerOpen = false }
}

@Composable private fun PlannerAgenda(
    entries: List<PlannerEntry>, cycles: List<CycleRecord>, tab: Int, date: LocalDate, today: LocalDate, zone: ZoneId,
    listView: Boolean, working: Boolean, onList: () -> Unit, onAdd: () -> Unit,
    onOpen: (PlannerEntry) -> Unit, onEdit: (PlannerEntry) -> Unit, onDelete: (PlannerEntry) -> Unit,
    onCompleted: (PlannerEntry, Boolean) -> Unit, onEditCycle: (CycleRecord) -> Unit, onDeleteCycle: (CycleRecord) -> Unit
) {
    val kind = listOf("日程", "待办", "纪念日", "经期记录")[tab]
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (tab == 3) "经期记录" else if (listView) "全部$kind" else date.format(plannerDayFormat), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            if (tab != 3) Text(if (listView) "按日期整理" else if (date == today) "今天" else if (date == today.plusDays(1)) "明天" else if (date == today.minusDays(1)) "昨天" else "${kind}安排", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (tab != 3) TextButton(onClick = onList) { Text(if (listView) "回到日历" else "全部") }
    }
    if (tab == 3) {
        Text("仅记录你填写的日期与备注，不预测周期。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
        if (cycles.isEmpty()) PlannerEmpty(kind, true, onAdd)
        cycles.forEach { record -> PlannerCycleRow(record, working, { onEditCycle(record) }, { onDeleteCycle(record) }) }
        return
    }
    if (tab == 1 && listView) {
        val groups = entries.groupBy { entry ->
            val due = Instant.ofEpochMilli(entry.startsAt).atZone(zone).toLocalDate()
            when { entry.completedAt != null -> 3; due < today -> 0; due == today -> 1; else -> 2 }
        }
        if (entries.isEmpty()) PlannerEmpty(kind, true, onAdd)
        listOf("已逾期", "今天", "之后", "已完成").forEachIndexed { index, label ->
            val group = groups[index]
            if (group != null) {
                Text("$label · ${group.size}", Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = if (index == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                group.sortedBy { it.startsAt }.forEach { entry ->
                    PlannerTimelineRow(PlannerOccurrence(entry, entry.startsAt, entry.endsAt), date, zone, true, working,
                        { onOpen(entry) }, { onEdit(entry) }, { onDelete(entry) }, { onCompleted(entry, it) })
                }
            }
        }
        return
    }
    val occurrences = remember(entries, date, zone, listView, today) {
        if (listView) entries.map { entry ->
            if (entry.kind == "anniversary") PlannerCalendar.nextAnniversary(entry, today, zone)
            else PlannerOccurrence(entry, entry.startsAt, entry.endsAt)
        }.sortedBy { it.startsAt } else PlannerCalendar.onDay(entries, date, zone)
    }
    if (occurrences.isEmpty()) {
        PlannerEmpty(kind, listView || entries.isEmpty(), onAdd)
        if (!listView && entries.isNotEmpty()) TextButton(onClick = onList) { Text("查看全部${kind}（${entries.size}）") }
    }
    var previousDay: LocalDate? = null
    occurrences.forEach { occurrence ->
        val day = Instant.ofEpochMilli(occurrence.startsAt).atZone(zone).toLocalDate()
        if (listView && day != previousDay) Text(day.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)), Modifier.padding(top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        previousDay = day
        PlannerTimelineRow(occurrence, date, zone, listView, working,
            { onOpen(occurrence.entry) }, { onEdit(occurrence.entry) }, { onDelete(occurrence.entry) }, { onCompleted(occurrence.entry, it) })
    }
}

@Composable private fun PlannerEmpty(kind: String, allEmpty: Boolean, onAdd: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Icon(if (kind == "待办") Icons.Default.Checklist else Icons.Default.EventAvailable, null, Modifier.padding(16.dp).size(32.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(if (allEmpty) "还没有$kind" else "这一天没有$kind", style = MaterialTheme.typography.titleMedium)
        Text("给重要的事留一点时间", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onAdd) { Icon(Icons.Default.Add, null, Modifier.size(20.dp)); Spacer(Modifier.width(4.dp)); Text("添加$kind") }
    }
}

@Composable private fun PlannerTimelineRow(
    occurrence: PlannerOccurrence, date: LocalDate, zone: ZoneId, showDate: Boolean, working: Boolean,
    onOpen: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onCompleted: (Boolean) -> Unit
) {
    val entry = occurrence.entry
    val start = Instant.ofEpochMilli(occurrence.startsAt).atZone(zone)
    val end = occurrence.endsAt?.let { Instant.ofEpochMilli(it).atZone(zone) }
    val largeText = LocalDensity.current.fontScale >= 1.5f
    val timeLabel = if (!showDate && start.toLocalDate() < date) "跨天" else start.format(plannerTimeFormat)
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
        if (!largeText) Column(Modifier.width(64.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(timeLabel, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            if (end != null) Text(if (end.toLocalDate() > date && !showDate) "次日" else end.format(plannerTimeFormat), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(onClick = onOpen, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    if (entry.kind == "task") Checkbox(entry.completedAt != null, onCompleted, enabled = !working, modifier = Modifier.semantics { contentDescription = if (entry.completedAt != null) "将${entry.title}标为未完成" else "完成${entry.title}" })
                    Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (largeText) Text(listOfNotNull(timeLabel, end?.format(plannerTimeFormat)).joinToString(" – "), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(entry.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textDecoration = if (entry.completedAt != null) TextDecoration.LineThrough else null)
                        if (showDate) Text(start.format(DateTimeFormatter.ofPattern("M月d日 HH:mm")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (entry.note.isNotBlank()) Text(entry.note, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (entry.shareWithRole) Icons.Default.PeopleAlt else Icons.Default.Lock, if (entry.shareWithRole) "已允许角色知道" else "仅自己可见", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.reminderMinutesBefore != null) Icon(Icons.Default.NotificationsActive, "${entry.reminderMinutesBefore}分钟前提醒", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            if (entry.kind == "anniversary") Text("每年", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (entry.zoneId != zone.id) Text(entry.zoneId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PlannerRowMenu(working, onEdit, onDelete, "${entry.title}的操作")
                }
            }
        }
    }
}

@Composable private fun PlannerCycleRow(record: CycleRecord, working: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.CalendarMonth, null, Modifier.padding(top = 8.dp, end = 12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${LocalDate.ofEpochDay(record.startedOn)}${record.endedOn?.let { " 至 ${LocalDate.ofEpochDay(it)}" }.orEmpty()}", style = MaterialTheme.typography.titleMedium)
            Text("流量：${when (record.flowLevel) { "light" -> "少"; "medium" -> "中"; "heavy" -> "多"; else -> "未填写" }} · ${if (record.shareWithRole) "允许角色知道" else "仅自己可见"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (record.note.isNotBlank()) Text(record.note, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        PlannerRowMenu(working, onEdit, onDelete, "${LocalDate.ofEpochDay(record.startedOn)}记录的操作")
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable private fun PlannerRowMenu(working: Boolean, onEdit: () -> Unit, onDelete: () -> Unit, label: String) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !working) { Icon(Icons.Default.MoreVert, label) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { expanded = false; onEdit() })
            DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) }, onClick = { expanded = false; onDelete() })
        }
    }
}

@Composable internal fun PlannerInformation(role: RoleEntity, loaded: PlannerData, onDismiss: () -> Unit) {
    val selection = remember(role, loaded) { PlannerRecords.select(role.scope(), loaded.entries, loaded.cycles, role.isRaw()) }
    var preview by rememberSaveable { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("分享与提醒") }, text = {
        Column(Modifier.heightIn(max = adaptiveDialogContentMaxHeight(480.dp)).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("默认仅自己可见", style = MaterialTheme.typography.titleMedium)
            Text("只有逐条允许分享的记录才可能加入之后的新请求；原味模型全部排除。")
            TextButton(onClick = { preview = !preview }) { Text(if (preview) "收起分享预览" else "查看聊天分享预览") }
            if (preview) {
                if (selection.instruction.isBlank()) Text("当前没有会加入聊天的记录。")
                else SelectionContainer { Text(selection.instruction.trim(), style = MaterialTheme.typography.bodyMedium) }
            }
            HorizontalDivider()
            Text("本机提醒", style = MaterialTheme.typography.titleMedium)
            Text("提醒使用系统单次非精确闹钟，可能延迟；强制停止应用、节电限制或关闭通知权限时可能无法显示。通知不含标题、备注、角色名或经期内容。", style = MaterialTheme.typography.bodyMedium)
            Text("日历按设备时区 ${ZoneId.systemDefault().id} 显示。编辑时保留记录原来的时区。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } })
}

@Composable internal fun PlannerDateField(value: String, label: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val date = LocalDate.parse(value)
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { pickerOpen = true }, modifier = modifier.heightIn(min = 64.dp), shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(12.dp)) {
        Icon(Icons.Default.CalendarMonth, null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (pickerOpen) PlannerDatePicker(date, { pickerOpen = false }) { onChange(it.toString()); pickerOpen = false }
}

@Composable internal fun PlannerTimeField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val time = LocalTime.parse(value)
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { pickerOpen = true }, modifier = modifier.heightIn(min = 64.dp), shape = MaterialTheme.shapes.small, contentPadding = PaddingValues(12.dp)) {
        Icon(Icons.Default.Schedule, null, Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("时间", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (pickerOpen) PlannerTimePicker(time, { pickerOpen = false }) { onChange(it.format(plannerTimeFormat)); pickerOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun PlannerDatePicker(date: LocalDate, onDismiss: () -> Unit, onDate: (LocalDate) -> Unit) {
    // Material date-picker milliseconds represent UTC calendar dates, not midnight in the stored record's zone.
    // Keeping the picker in Compose also preserves the app theme and status-bar appearance after dismissal.
    val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..9999)
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
            val selected = picker.selectedDateMillis
            if (selected != null) onDate(Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate())
        }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) {
        DatePicker(state = picker, modifier = Modifier.heightIn(max = adaptiveDialogContentMaxHeight(500.dp)).verticalScroll(rememberScrollState()),
            title = { Text("选择日期", Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp), style = MaterialTheme.typography.titleMedium) },
            headline = {
                val selected = picker.selectedDateMillis
                Text(if (selected == null) "请选择日期" else Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
            }, showModeToggle = false)
    }
}

@Composable private fun PlannerTimePicker(time: LocalTime, onDismiss: () -> Unit, onTime: (LocalTime) -> Unit) {
    var hour by rememberSaveable { mutableStateOf(time.hour.toString().padStart(2, '0')) }
    var minute by rememberSaveable { mutableStateOf(time.minute.toString().padStart(2, '0')) }
    val parsedHour = hour.toIntOrNull()
    val parsedMinute = minute.toIntOrNull()
    val valid = parsedHour != null && parsedHour in 0..23 && parsedMinute != null && parsedMinute in 0..59
    val hourField: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(hour, { if (it.length <= 2 && it.all(Char::isDigit)) hour = it }, modifier,
            label = { Text("小时") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = parsedHour == null || parsedHour !in 0..23)
    }
    val minuteField: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(minute, { if (it.length <= 2 && it.all(Char::isDigit)) minute = it }, modifier,
            label = { Text("分钟") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = parsedMinute == null || parsedMinute !in 0..59)
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择时间") }, text = {
        Column(Modifier.heightIn(max = adaptiveDialogContentMaxHeight(360.dp)).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("24 小时制 · 小时 0–23，分钟 0–59", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (LocalDensity.current.fontScale >= 1.5f) {
                hourField(Modifier.fillMaxWidth()); minuteField(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                hourField(Modifier.weight(1f)); minuteField(Modifier.weight(1f))
            }
        }
    }, confirmButton = { TextButton(enabled = valid, onClick = {
        if (parsedHour != null && parsedHour in 0..23 && parsedMinute != null && parsedMinute in 0..59) onTime(LocalTime.of(parsedHour, parsedMinute))
    }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
