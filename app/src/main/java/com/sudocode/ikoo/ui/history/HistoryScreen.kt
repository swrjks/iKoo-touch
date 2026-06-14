package com.sudocode.ikoo.ui.history

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sudocode.ikoo.history.HistoryEventEntity
import com.sudocode.ikoo.history.HistoryRepository
import com.sudocode.ikoo.ui.components.PremiumCard
import com.sudocode.ikoo.ui.components.CardVariant
import com.sudocode.ikoo.ui.theme.*
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 8.dp
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val repository = remember(context) { HistoryRepository.getInstance(context.applicationContext) }
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(FilterType.ALL) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var eventToDelete by remember { mutableStateOf<HistoryEventEntity?>(null) }
    var rangeToDelete by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    val historyFlow = remember(query, selectedFilter, repository) {
        repository.search(query, selectedFilter.toHistoryIntent())
    }
    val allEvents by historyFlow.collectAsState(initial = emptyList())
    val events = remember(allEvents, selectedDate) {
        selectedDate?.let { date ->
            val start = date.startOfDayMillis()
            val end = date.plusDays(1).startOfDayMillis()
            allEvents.filter { it.timestampMillis in start until end }
        } ?: allEvents
    }

    // Group events by date
    val groupedEvents = remember(events) { groupEventsByDate(events) }
    val savedDates = remember(allEvents) {
        allEvents.map { it.timestampMillis.toLocalDate() }.toSet()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(InkBlack)
    ) {
        PremiumHistoryBackground()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enhanced Header
            item { AnimatedHistoryHeader(count = events.size) }

            // Enhanced Search & Filters
            item {
                EnhancedSearchAndFiltersCard(
                    query = query,
                    selectedFilter = selectedFilter,
                    onQueryChange = { query = it },
                    onFilterSelect = { selectedFilter = it },
                    onClearAll = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        eventToDelete = null
                        rangeToDelete = null
                        showDeleteConfirm = true
                    },
                    hasEvents = allEvents.isNotEmpty()
                )
            }

            item {
                HistoryCalendarCard(
                    visibleMonth = visibleMonth,
                    selectedDate = selectedDate,
                    savedDates = savedDates,
                    onPreviousMonth = { visibleMonth = visibleMonth.minusMonths(1) },
                    onNextMonth = { visibleMonth = visibleMonth.plusMonths(1) },
                    onDateSelected = { selectedDate = if (selectedDate == it) null else it },
                    onClearDate = { selectedDate = null },
                    onDeleteSelectedDate = {
                        selectedDate?.let { date ->
                            rangeToDelete = date.startOfDayMillis() to date.plusDays(1).startOfDayMillis()
                            eventToDelete = null
                            showDeleteConfirm = true
                        }
                    }
                )
            }

            // Grouped Timeline Events
            if (events.isEmpty()) {
                item { EnhancedEmptyHistoryCard(query = query, filter = selectedFilter) }
            } else {
                groupedEvents.forEach { (dateLabel, dateEvents) ->
                    item { DateHeader(dateLabel = dateLabel, count = dateEvents.size) }
                    items(
                        items = dateEvents,
                        key = { it.id }
                    ) { event ->
                        EnhancedSwipeHistoryItem(
                            event = event,
                            repository = repository,
                            onDelete = { eventToDelete = it; showDeleteConfirm = true }
                        )
                    }
                }
            }
        }

        // Delete Confirmation Dialog
        AnimatedVisibility(
            visible = showDeleteConfirm,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            DeleteConfirmationDialog(
                event = eventToDelete,
                rangeToDelete = rangeToDelete,
                onConfirm = {
                    when {
                        eventToDelete != null -> repository.delete(eventToDelete!!)
                        rangeToDelete != null -> repository.deleteBetween(rangeToDelete!!.first, rangeToDelete!!.second)
                        else -> repository.clearAll()
                    }
                    showDeleteConfirm = false
                    eventToDelete = null
                    rangeToDelete = null
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDismiss = {
                    showDeleteConfirm = false
                    eventToDelete = null
                    rangeToDelete = null
                }
            )
        }
    }
}

enum class FilterType {
    ALL, CALENDAR, REMINDER, TASK, NONE
}

private fun groupEventsByDate(events: List<HistoryEventEntity>): List<Pair<String, List<HistoryEventEntity>>> {
    val today = formatDateGroup(System.currentTimeMillis())
    val yesterday = formatDateGroup(System.currentTimeMillis() - 86400000)
    val thisWeek = "This Week"
    val older = "Older"

    val grouped = mutableMapOf<String, MutableList<HistoryEventEntity>>()

    events.forEach { event ->
        val group = when {
            formatDateGroup(event.timestampMillis) == today -> "Today"
            formatDateGroup(event.timestampMillis) == yesterday -> "Yesterday"
            event.timestampMillis > System.currentTimeMillis() - 7 * 86400000 -> thisWeek
            else -> older
        }
        grouped.getOrPut(group) { mutableListOf() }.add(event)
    }

    return listOf("Today", "Yesterday", thisWeek, older)
        .filter { grouped.containsKey(it) }
        .map { it to grouped[it]!! }
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun LocalDate.startOfDayMillis(): Long {
    return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun formatDateGroup(timestamp: Long): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return "${calendar.get(java.util.Calendar.DAY_OF_MONTH)}/${calendar.get(java.util.Calendar.MONTH) + 1}/${calendar.get(java.util.Calendar.YEAR)}"
}

@Composable
private fun AnimatedHistoryHeader(count: Int) {
    var animatedCount by remember { mutableStateOf(0) }

    LaunchedEffect(count) {
        for (i in 0..count step maxOf(1, count / 20)) {
            animatedCount = i
            delay(8)
        }
        animatedCount = count
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Memory",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = "Timeline",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = ElectricMint,
            letterSpacing = (-0.5).sp
        )
        Text(
            text = if (count == 0) "No memories yet" else "$animatedCount saved memories",
            fontSize = 14.sp,
            color = MutedFrost
        )
    }
}

@Composable
private fun EnhancedSearchAndFiltersCard(
    query: String,
    selectedFilter: FilterType,
    onQueryChange: (String) -> Unit,
    onFilterSelect: (FilterType) -> Unit,
    onClearAll: () -> Unit,
    hasEvents: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    PremiumCard(
        variant = CardVariant.PREMIUM,
        cornerRadius = 28,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enhanced Search Field
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = {
                    onQueryChange(it)
                    isExpanded = it.isNotEmpty()
                },
                singleLine = true,
                placeholder = {
                    Text(
                        "Search memories...",
                        color = MutedFrost,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = ElectricMint
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MutedFrost)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricMint,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Frost,
                    unfocusedTextColor = Frost
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Frost),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {})
            )

            // Filter Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(FilterType.values()) { filter ->
                    AnimatedFilterChip(
                        label = filter.displayName(),
                        selected = selectedFilter == filter,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onFilterSelect(filter)
                        }
                    )
                }
            }

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                EnhancedActionButton(
                    label = "Clear Search",
                    icon = Icons.Default.Clear,
                    enabled = query.isNotBlank(),
                    onClick = { onQueryChange("") },
                    modifier = Modifier.weight(1f)
                )
                EnhancedActionButton(
                    label = "Clear All",
                    icon = Icons.Default.DeleteSweep,
                    enabled = hasEvents,
                    destructive = true,
                    onClick = onClearAll,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HistoryCalendarCard(
    visibleMonth: YearMonth,
    selectedDate: LocalDate?,
    savedDates: Set<LocalDate>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onClearDate: () -> Unit,
    onDeleteSelectedDate: () -> Unit
) {
    val monthDays = remember(visibleMonth) { visibleMonth.calendarCells() }
    val weekLabels = remember {
        DayOfWeek.values().map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2) }
    }

    PremiumCard(
        variant = CardVariant.PREMIUM,
        cornerRadius = 28,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month", tint = Frost)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        visibleMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Frost
                    )
                    Text(
                        visibleMonth.year.toString(),
                        fontSize = 12.sp,
                        color = MutedFrost
                    )
                }
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month", tint = Frost)
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                weekLabels.forEach { label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedFrost
                    )
                }
            }

            monthDays.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { day ->
                        CalendarDayCell(
                            date = day,
                            inMonth = day.month == visibleMonth.month,
                            selected = day == selectedDate,
                            hasSavedEvents = savedDates.contains(day),
                            onClick = { onDateSelected(day) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = selectedDate != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    EnhancedActionButton(
                        label = "Show All",
                        icon = Icons.Default.CalendarViewMonth,
                        enabled = true,
                        onClick = onClearDate,
                        modifier = Modifier.weight(1f)
                    )
                    EnhancedActionButton(
                        label = "Delete Day",
                        icon = Icons.Default.Delete,
                        enabled = selectedDate?.let { savedDates.contains(it) } == true,
                        destructive = true,
                        onClick = onDeleteSelectedDate,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate,
    inMonth: Boolean,
    selected: Boolean,
    hasSavedEvents: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = when {
        selected -> ElectricMint.copy(alpha = 0.22f)
        hasSavedEvents -> SignalBlue.copy(alpha = 0.14f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val borderColor = when {
        selected -> ElectricMint
        hasSavedEvents -> SignalBlue.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (selected || hasSavedEvents) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    !inMonth -> MutedFrost.copy(alpha = 0.35f)
                    selected -> ElectricMint
                    else -> Frost
                }
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (hasSavedEvents) ElectricMint else Color.Transparent)
            )
        }
    }
}

@Composable
private fun AnimatedFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(),
        label = "filterChipScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) {
                    Modifier.background(Brush.horizontalGradient(listOf(ElectricMint, SignalBlue)))
                } else {
                    Modifier.background(Color.White.copy(alpha = 0.08f))
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.Black else MutedFrost
        )
    }
}

@Composable
private fun EnhancedActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val color = if (destructive) WarmCoral else ElectricMint
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(),
        label = "actionScale"
    )

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = if (enabled) 0.15f else 0.05f))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) color else MutedFrost.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) color else MutedFrost.copy(alpha = 0.55f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedSwipeHistoryItem(
    event: HistoryEventEntity,
    repository: HistoryRepository,
    onDelete: (HistoryEventEntity) -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete(event)
                true
            } else {
                false
            }
        }
    )

    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 })
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(28.dp))
                        .background(WarmCoral.copy(alpha = 0.15f))
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = WarmCoral,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Delete",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = WarmCoral
                        )
                    }
                }
            }
        ) {
            EnhancedHistoryTimelineCard(
                event = event,
                onDelete = { onDelete(event) }
            )
        }
    }
}

@Composable
private fun EnhancedHistoryTimelineCard(
    event: HistoryEventEntity,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Animated Timeline Dot with connecting line
        EnhancedTimelineDot(
            intent = event.detectedIntent,
            isExpanded = isExpanded
        )

        PremiumCard(
            variant = CardVariant.PREMIUM,
            cornerRadius = 24,
            modifier = Modifier.weight(1f),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isExpanded = !isExpanded
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row with Intent and Confidence
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Intent Icon
                        IntentIcon(intent = event.detectedIntent)
                        Text(
                            text = event.detectedIntent.readableIntent(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Frost,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Animated Confidence Badge
                    AnimatedConfidenceBadge(confidence = event.confidence)
                }

                // Raw Text (truncated or expanded)
                Text(
                    text = event.rawText,
                    fontSize = if (isExpanded) 14.sp else 13.sp,
                    color = MutedFrost,
                    maxLines = if (isExpanded) 4 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Entity Grid
                EnhancedHistoryEntityGrid(event = event)

                // Footer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = MutedFrost,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formatTimeAgo(event.timestampMillis),
                            fontSize = 11.sp,
                            color = MutedFrost.copy(alpha = 0.76f)
                        )
                    }

                    // Action indicator
                    if (event.actionTaken.isNotBlank() && event.actionTaken != "None") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ElectricMint)
                            )
                            Text(
                                text = event.actionTaken.take(20),
                                fontSize = 11.sp,
                                color = ElectricMint
                            )
                        }
                    }

                    DeleteIconButton(onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun IntentIcon(intent: String) {
    val (icon, color) = when (intent) {
        "CALENDAR_EVENT" -> "📅" to ElectricMint
        "REMINDER" -> "⏰" to SignalBlue
        "TASK" -> "✅" to SoftViolet
        else -> "💬" to WarmCoral
    }

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, fontSize = 16.sp)
    }
}

@Composable
private fun AnimatedConfidenceBadge(confidence: Float) {
    val percent = (confidence * 100).toInt()
    val color = when {
        percent >= 80 -> ElectricMint
        percent >= 50 -> Color(0xFFFFB347)
        else -> WarmCoral
    }

    var displayedPercent by remember { mutableStateOf(0) }

    LaunchedEffect(percent) {
        for (i in 0..percent step maxOf(1, percent / 20)) {
            displayedPercent = i
            delay(8)
        }
        displayedPercent = percent
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            "$displayedPercent%",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun EnhancedHistoryEntityGrid(event: HistoryEventEntity) {
    val entities = listOfNotNull(
        event.extractedTitle?.let { "Title" to it },
        event.datePhrase?.let { "Date" to it },
        event.timePhrase?.let { "Time" to it },
        event.location?.let { "Location" to it }
    )

    if (entities.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        entities.forEach { (label, value) ->
            EnhancedEntityLine(label = label, value = value)
        }
    }
}

@Composable
private fun EnhancedEntityLine(label: String, value: String) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInHorizontally()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MutedFrost
            )
            Text(
                text = value,
                fontSize = 13.sp,
                color = Frost,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.7f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun EnhancedTimelineDot(intent: String, isExpanded: Boolean) {
    val color = when (intent) {
        "CALENDAR_EVENT" -> ElectricMint
        "REMINDER" -> SignalBlue
        "TASK" -> SoftViolet
        else -> WarmCoral
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(if (isExpanded) 18.dp else 14.dp)
                .graphicsLayer {
                    shadowElevation = if (isExpanded) 8f else 4f
                    spotShadowColor = color
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(color, color.copy(alpha = 0.5f))
                    )
                )
        )

        // Connecting Line
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(if (isExpanded) 200.dp else 160.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
    }
}

@Composable
private fun DeleteIconButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(),
        label = "deleteScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Icon(
            Icons.Default.DeleteOutline,
            contentDescription = "Delete",
            tint = WarmCoral,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DateHeader(dateLabel: String, count: Int) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(50)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInHorizontally()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                dateLabel,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Frost
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.1f))
            )
            Text(
                "$count",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricMint
            )
        }
    }
}

@Composable
private fun EnhancedEmptyHistoryCard(query: String, filter: FilterType) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn()
    ) {
        PremiumCard(
            variant = CardVariant.GAMING,
            cornerRadius = 32,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(ElectricMint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = ElectricMint,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = if (query.isNotBlank() || filter != FilterType.ALL) "No Results Found" else "Your Memory Timeline",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Frost
                )

                Text(
                    text = when {
                        query.isNotBlank() -> "No memories match \"$query\""
                        filter != FilterType.ALL -> "No ${filter.displayName()} events found"
                        else -> "Events detected from your screen and notifications will appear here"
                    },
                    fontSize = 14.sp,
                    color = MutedFrost,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    event: HistoryEventEntity?,
    rangeToDelete: Pair<Long, Long>?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(32.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(WarmCoral.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = WarmCoral,
                    modifier = Modifier.size(28.dp)
                )
            }

            Text(
                when {
                    event != null -> "Delete Memory?"
                    rangeToDelete != null -> "Delete Day?"
                    else -> "Delete All?"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Frost
            )

            Text(
                when {
                    event != null -> "This saved memory will be removed."
                    rangeToDelete != null -> "All saved memories on this date will be removed."
                    else -> "Every saved memory will be removed."
                },
                fontSize = 13.sp,
                color = MutedFrost,
                textAlign = TextAlign.Center
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MutedFrost
                    )
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmCoral
                    )
                ) {
                    Text("Delete", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PremiumHistoryBackground() {
    val infiniteTransition = rememberInfiniteTransition()
    val offsetX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 100f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgX"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(tween(10000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgY"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .size(400.dp)
                .graphicsLayer {
                    translationX = -120f
                    translationY = -90f
                }
                .blur(80.dp)
                .clip(CircleShape)
                .background(ElectricMint.copy(alpha = 0.15f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(350.dp)
                .graphicsLayer {
                    translationX = 130f
                    translationY = 60f
                }
                .blur(80.dp)
                .clip(CircleShape)
                .background(SoftViolet.copy(alpha = 0.2f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            InkBlack.copy(alpha = 0.1f),
                            InkBlack.copy(alpha = 0.8f),
                            InkBlack
                        ),
                        center = Offset(offsetX, offsetY),
                        radius = 600f
                    )
                )
        )
    }
}

// Extension functions
private fun String.readableIntent(): String {
    return lowercase()
        .split("_")
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
}

private fun FilterType.displayName(): String {
    return when (this) {
        FilterType.ALL -> "All"
        FilterType.CALENDAR -> "Calendar"
        FilterType.REMINDER -> "Reminder"
        FilterType.TASK -> "Task"
        FilterType.NONE -> "Other"
    }
}

private fun FilterType.toHistoryIntent(): String? {
    return when (this) {
        FilterType.ALL -> null
        FilterType.CALENDAR -> "CALENDAR_EVENT"
        FilterType.REMINDER -> "REMINDER"
        FilterType.TASK -> "TASK"
        FilterType.NONE -> "NONE"
    }
}

private fun YearMonth.calendarCells(): List<LocalDate> {
    val firstOfMonth = atDay(1)
    val daysBefore = firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
    val firstCell = firstOfMonth.minusDays(daysBefore.toLong())
    return (0 until 42).map { firstCell.plusDays(it.toLong()) }
}

private fun formatTimeAgo(millis: Long): String {
    val diff = System.currentTimeMillis() - millis
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}
