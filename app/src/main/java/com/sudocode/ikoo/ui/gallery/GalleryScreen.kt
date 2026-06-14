package com.sudocode.ikoo.ui.gallery

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sudocode.ikoo.core.ai.AIEngineRegistry
import com.sudocode.ikoo.gallery_ai.GalleryPhoto
import com.sudocode.ikoo.gallery_ai.GalleryRepository
import com.sudocode.ikoo.gallery_ai.SmartSearchParser
import com.sudocode.ikoo.ui.components.PremiumCard
import com.sudocode.ikoo.ui.components.CardVariant
import com.sudocode.ikoo.ui.theme.ElectricMint
import com.sudocode.ikoo.ui.theme.Frost
import com.sudocode.ikoo.ui.theme.InkBlack
import com.sudocode.ikoo.ui.theme.MutedFrost
import com.sudocode.ikoo.ui.theme.SignalBlue
import com.sudocode.ikoo.ui.theme.SoftViolet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val QuickQueries = listOf(
    "Gujarat",
    "Mysore Palace",
    "last month",
    "temple photos",
    "beach photos",
    "screenshots",
    "birthday",
    "sunsets"
)

@Composable
fun GalleryScreen(modifier: Modifier = Modifier, bottomPadding: Dp = 8.dp) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    var allPhotos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    var filteredPhotos by remember { mutableStateOf<List<GalleryPhoto>>(emptyList()) }
    var selectedPhoto by remember { mutableStateOf<GalleryPhoto?>(null) }
    var query by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("Getting ready...") }
    var searchSummary by remember { mutableStateOf("Search your photos by place, date, or what's in them") }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showSearchFilters by remember { mutableStateOf(false) }
    var gridColumns by remember { mutableStateOf(3) }
    var pendingDeletePhoto by remember { mutableStateOf<GalleryPhoto?>(null) }

    fun removePhoto(photo: GalleryPhoto) {
        allPhotos = allPhotos.filterNot { it.id == photo.id }
        filteredPhotos = filteredPhotos.filterNot { it.id == photo.id }
        if (selectedPhoto?.id == photo.id) selectedPhoto = null
        statusMessage = "Deleted memory"
        searchSummary = "${filteredPhotos.size} photos remaining"
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val photo = pendingDeletePhoto
        pendingDeletePhoto = null
        if (result.resultCode == Activity.RESULT_OK && photo != null) {
            removePhoto(photo)
        }
    }

    val readImagesPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val galleryPermissions = buildList {
        add(readImagesPermission)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }.toTypedArray()

    fun updateForPhotos(photos: List<GalleryPhoto>) {
        allPhotos = photos
        filteredPhotos = photos
        selectedPhoto = photos.firstOrNull()
        val located = photos.count { it.hasLocation }
        val screenshots = photos.count { it.isScreenshot }
        statusMessage = "${photos.size} photos"
        searchSummary = "$located locations • $screenshots screenshots"
        isLoading = false
    }

    fun loadGallery() {
        scope.launch {
            isLoading = true
            statusMessage = "Loading your memories..."
            val photos = withContext(Dispatchers.IO) { GalleryRepository.loadPhotos(context) }
            updateForPhotos(photos)
        }
    }

    fun performSearch(text: String, category: String? = null) {
        val searchText = category ?: text
        if (searchText.isBlank()) {
            filteredPhotos = allPhotos
            selectedPhoto = null
            statusMessage = "${allPhotos.size} photos"
            searchSummary = "Showing all your memories"
            return
        }
        scope.launch {
            isLoading = true
            statusMessage = "Searching..."
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            val qwenUsed = AIEngineRegistry.active()?.isReady() == true
            val parsed = withContext(Dispatchers.Default) {
                SmartSearchParser.parseWithAi(searchText, AIEngineRegistry.active())
            }
            val results = withContext(Dispatchers.Default) {
                GalleryRepository.filter(allPhotos, parsed)
            }
            filteredPhotos = results
            selectedPhoto = null
            statusMessage = if (results.isEmpty()) "No photos found" else "${results.size} photos"
            searchSummary = GalleryRepository.explainSearch(parsed, results, qwenUsed)
            isLoading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[readImagesPermission] == true ||
                context.checkSelfPermission(readImagesPermission) == PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        if (granted) loadGallery() else {
            isLoading = false
            statusMessage = "Photo permission required"
            searchSummary = "Grant photo access to unlock AI-powered search"
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isListening = true
            startVoiceSearch(context) { spoken ->
                isListening = false
                if (spoken.isNotBlank()) {
                    query = spoken
                    performSearch(spoken)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.Default) {
            AIEngineRegistry.initialize(context)
        }
        hasPermission = context.checkSelfPermission(readImagesPermission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) loadGallery() else permissionLauncher.launch(galleryPermissions)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(InkBlack)
    ) {
        AnimatedMeshGalleryBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enhanced Header
            AnimatedGalleryHeader(
                statusMessage = statusMessage,
                onRefresh = { if (hasPermission) loadGallery() else permissionLauncher.launch(galleryPermissions) }
            )

            // Enhanced Search Bar with Filters
            EnhancedSearchBar(
                query = query,
                isListening = isListening,
                showFilters = showSearchFilters,
                onQueryChange = { query = it },
                onSearch = { performSearch(query) },
                onMic = {
                    val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        isListening = true
                        startVoiceSearch(context) { spoken ->
                            isListening = false
                            if (spoken.isNotBlank()) {
                                query = spoken
                                performSearch(spoken)
                            }
                        }
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onToggleFilters = { showSearchFilters = !showSearchFilters },
                onGridChange = { gridColumns = it }
            )

            // Quick Categories Row
            QuickCategoriesRow(
                selectedCategory = selectedCategory,
                onCategorySelected = { category ->
                    selectedCategory = category
                    performSearch(query, category)
                }
            )

            // Gallery Stats
            AnimatedGalleryStatsRow(
                total = allPhotos.size,
                located = allPhotos.count { it.hasLocation },
                screenshots = allPhotos.count { it.isScreenshot }
            )

            // Search Summary with animation
            AnimatedSearchSummary(searchSummary)

            // Main Content Area
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when {
                    isLoading -> EnhancedLoadingIndicator()
                    !hasPermission -> EnhancedPermissionPrompt {
                        permissionLauncher.launch(galleryPermissions)
                    }
                    filteredPhotos.isEmpty() -> EnhancedEmptyGalleryState(query = query)
                    else -> {
                        // Pinterest-style staggered grid
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = (120.dp * (3f / gridColumns))),
                            contentPadding = PaddingValues(bottom = bottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredPhotos, key = { it.id }) { photo ->
                                EnhancedPhotoTile(
                                    photo = photo,
                                    onClick = { selectedPhoto = photo }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Enhanced Photo Detail Sheet
        selectedPhoto?.let { photo ->
            EnhancedPhotoDetailSheet(
                photo = photo,
                onDismiss = { selectedPhoto = null },
                onShare = { context.sharePhoto(photo) },
                onDelete = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pendingDeletePhoto = photo
                        val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(photo.uri))
                        deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    } else {
                        scope.launch(Dispatchers.IO) {
                            val deleted = context.contentResolver.delete(photo.uri, null, null) > 0
                            withContext(Dispatchers.Main) {
                                if (deleted) removePhoto(photo)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun AnimatedGalleryHeader(
    statusMessage: String,
    onRefresh: () -> Unit
) {
    var isRefreshing by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Animated Logo Box
        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
            label = "logoRotation"
        )

        Box(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        listOf(ElectricMint, SignalBlue, SoftViolet)
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = null,
                tint = InkBlack,
                modifier = Modifier.size(28.dp)
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Memories",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                ),
                color = Frost
            )
            AnimatedContent(
                targetState = statusMessage,
                transitionSpec = { fadeIn() + slideInVertically() togetherWith fadeOut() + slideOutVertically() }
            ) { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedFrost
                )
            }
        }

        // Animated Refresh Button
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isRefreshing = true
                onRefresh()
            }
        ) {
            val rotation by animateFloatAsState(
                targetValue = if (isRefreshing) 360f else 0f,
                animationSpec = tween(500),
                label = "refreshRotation"
            )
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Rescan gallery",
                tint = Frost,
                modifier = Modifier.graphicsLayer { rotationZ = rotation }
            )
        }

        LaunchedEffect(isRefreshing) {
            if (isRefreshing) {
                delay(1000)
                isRefreshing = false
            }
        }
    }
}

@Composable
private fun EnhancedSearchBar(
    query: String,
    isListening: Boolean,
    showFilters: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onMic: () -> Unit,
    onToggleFilters: () -> Unit,
    onGridChange: (Int) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Main Search Row
        PremiumCard(
            variant = CardVariant.PREMIUM,
            cornerRadius = 28,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated Search Icon
                val searchPulse by rememberInfiniteTransition().animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                    label = "searchPulse"
                )
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = ElectricMint.copy(alpha = searchPulse),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                TextField(
                    value = query,
                    onValueChange = {
                        onQueryChange(it)
                        isExpanded = it.isNotEmpty()
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Search photos naturally...",
                            color = MutedFrost,
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Frost),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = ElectricMint
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() })
                )

                // Voice Search Button
                IconButton(onClick = onMic) {
                    val micScale by animateFloatAsState(
                        targetValue = if (isListening) 1.2f else 1f,
                        animationSpec = spring(),
                        label = "micScale"
                    )
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice search",
                        tint = if (isListening) ElectricMint else MutedFrost,
                        modifier = Modifier.graphicsLayer { scaleX = micScale; scaleY = micScale }
                    )
                }

                // Filter Button
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleFilters()
                }) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "Filters",
                        tint = if (showFilters) ElectricMint else MutedFrost
                    )
                }
            }
        }

        // Expanded Filters Panel
        AnimatedVisibility(
            visible = showFilters,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            PremiumCard(variant = CardVariant.MINIMAL, cornerRadius = 24) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "FILTERS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MutedFrost,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(label = "Grid", onClick = { onGridChange(3) })
                        FilterChip(label = "Date: Newest", onClick = {})
                        FilterChip(label = "Location", onClick = {})
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCategoriesRow(
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    val categories = listOf("All", "Beach", "Mountains", "City", "Food", "People", "Screenshots")

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(categories.size) { index ->
            val category = categories[index]
            val isSelected = (selectedCategory == category) || (category == "All" && selectedCategory == null)

            AnimatedCategoryChip(
                label = category,
                selected = isSelected,
                onClick = {
                    onCategorySelected(if (category == "All") null else category)
                }
            )
        }
    }
}

@Composable
private fun AnimatedCategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(),
        label = "chipScale"
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
                onClick = { onClick() }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.Black else Frost
        )
    }
}

@Composable
private fun AnimatedGalleryStatsRow(total: Int, located: Int, screenshots: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedStatPill(
            icon = Icons.Default.PhotoLibrary,
            label = "Photos",
            value = total.toString(),
            color = ElectricMint,
            modifier = Modifier.weight(1f)
        )
        AnimatedStatPill(
            icon = Icons.Default.LocationOn,
            label = "Places",
            value = located.toString(),
            color = SignalBlue,
            modifier = Modifier.weight(1f)
        )
        AnimatedStatPill(
            icon = Icons.Default.Screenshot,
            label = "Screenshots",
            value = screenshots.toString(),
            color = SoftViolet,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AnimatedStatPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    var animatedValue by remember { mutableStateOf("0") }

    LaunchedEffect(value) {
        val target = value.toIntOrNull() ?: 0
        for (i in 0..target step maxOf(1, target / 20)) {
            animatedValue = i.toString()
            delay(10)
        }
        animatedValue = value
    }

    PremiumCard(
        modifier = modifier,
        variant = CardVariant.PREMIUM,
        cornerRadius = 20,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    animatedValue,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = Frost
                )
                Text(label, style = MaterialTheme.typography.labelSmall, color = MutedFrost)
            }
        }
    }
}

@Composable
private fun AnimatedSearchSummary(summary: String) {
    AnimatedContent(
        targetState = summary,
        transitionSpec = {
            fadeIn() + slideInHorizontally() togetherWith fadeOut() + slideOutHorizontally()
        },
        label = "searchSummary"
    ) { text ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = ElectricMint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = MutedFrost,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EnhancedLoadingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "loadingRotation"
        )

        Box(
            modifier = Modifier
                .size(60.dp)
                .graphicsLayer { rotationZ = rotation }
        ) {
            CircularProgressIndicator(
                color = ElectricMint,
                strokeWidth = 3.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            "Loading your memories...",
            color = MutedFrost,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun EnhancedPermissionPrompt(onGrant: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ElectricMint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.PhotoLibrary,
                contentDescription = null,
                tint = ElectricMint,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            "Photo Access Required",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Frost
        )
        Text(
            "iKoo needs access to your photos to enable AI-powered search and organization.",
            fontSize = 14.sp,
            color = MutedFrost,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricMint),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("Grant Access", color = Color.Black, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EnhancedEmptyGalleryState(query: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(32.dp)
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ElectricMint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = null,
                tint = ElectricMint,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            if (query.isBlank()) "No Photos Found" else "No Results for \"$query\"",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Frost
        )
        Text(
            if (query.isBlank()) {
                "Take some photos or grant permission to see them here."
            } else {
                "Try a different search term or browse by category."
            },
            fontSize = 14.sp,
            color = MutedFrost,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EnhancedPhotoTile(
    photo: GalleryPhoto,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tileScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isPressed) 4f else 8f
            }
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
    ) {
        // Main Image
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient Overlay
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
        )

        // Bottom Info
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = photo.inferredPlace?.canonicalName?.take(20)
                    ?: photo.visionResult.searchableTokens().firstOrNull()?.take(15)
                    ?: if (photo.isScreenshot) "📸 Screenshot" else photo.formattedDate().take(12),
                style = MaterialTheme.typography.labelSmall,
                color = Frost,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Hover/Selection Indicator
        if (isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.1f))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedPhotoDetailSheet(
    photo: GalleryPhoto,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var currentPhoto by remember { mutableStateOf(photo) }
    var showDetails by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0F),
        contentColor = Frost,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Hero Image with parallax effect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
            ) {
                AsyncImage(
                    model = currentPhoto.uri,
                    contentDescription = currentPhoto.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Gradient overlays
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.6f),
                                    Color.Black.copy(alpha = 0.9f)
                                ),
                                startY = 0.5f
                            )
                        )
                )

                // Top actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }

            // Photo Info
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Date with icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = ElectricMint,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = currentPhoto.formattedDate(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Frost,
                        fontSize = 14.sp
                    )
                }

                // Location
                currentPhoto.inferredPlace?.let { place ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = ElectricMint,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = place.canonicalName.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodyMedium,
                            color = ElectricMint,
                            fontSize = 14.sp
                        )
                    }
                }

                // AI Tags
                val tags = currentPhoto.visionResult.searchableTokens().take(15).toList()
                if (tags.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "AI DETECTED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedFrost,
                            letterSpacing = 1.sp
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tags.size) { index ->
                                AnimatedTag(tag = tags[index])
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onShare()
                        }
                    )
                    ActionButton(
                        icon = Icons.Default.Info,
                        label = "Details",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDetails = true
                        }
                    )
                    ActionButton(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        },
                        destructive = true
                    )
                }
            }
        }
    }

    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close")
                }
            },
            title = { Text("Memory details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailLine("Name", currentPhoto.displayName)
                    DetailLine("Date", currentPhoto.formattedDate())
                    DetailLine("Size", "${currentPhoto.width} × ${currentPhoto.height}")
                    DetailLine("Type", currentPhoto.mimeType.ifBlank { "Image" })
                    DetailLine("Folder", currentPhoto.relativePath.ifBlank { "Pictures" })
                    DetailLine("Location", currentPhoto.inferredPlace?.canonicalName ?: "No location")
                    DetailLine(
                        "AI tags",
                        currentPhoto.visionResult.searchableTokens().take(10).joinToString(", ").ifBlank { "No tags" }
                    )
                }
            },
            containerColor = Color(0xFF081528),
            titleContentColor = Color.White,
            textContentColor = Frost
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = MutedFrost, fontWeight = FontWeight.Bold)
        Text(value, fontSize = 14.sp, color = Frost)
    }
}

@Composable
private fun AnimatedTag(tag: String) {
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn() + fadeIn()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(ElectricMint.copy(alpha = 0.12f))
                .border(1.dp, ElectricMint.copy(alpha = 0.3f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                color = ElectricMint,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(),
        label = "filterChipScale"
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 12.sp, color = MutedFrost)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(),
        label = "actionScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (destructive) Color(0xFFFF1493) else ElectricMint,
            modifier = Modifier.size(24.dp)
        )
        Text(
            label,
            fontSize = 11.sp,
            color = if (destructive) Color(0xFFFF1493) else MutedFrost
        )
    }
}

@Composable
private fun AnimatedMeshGalleryBackground() {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val gradient = Brush.radialGradient(
                    colors = listOf(
                        SignalBlue.copy(alpha = 0.15f),
                        SoftViolet.copy(alpha = 0.08f),
                        InkBlack
                    ),
                    center = Offset(offsetX, offsetY),
                    radius = 800f
                )
                drawRect(brush = gradient)
            }
    )
}

// Preserved helper functions
private fun startVoiceSearch(context: android.content.Context, onResult: (String) -> Unit) {
    if (!SpeechRecognizer.isRecognitionAvailable(context)) {
        onResult("")
        return
    }

    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }

    recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onResults(results: android.os.Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            onResult(matches?.firstOrNull().orEmpty())
            recognizer.destroy()
        }

        override fun onError(error: Int) {
            onResult("")
            recognizer.destroy()
        }

        override fun onReadyForSpeech(params: android.os.Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: android.os.Bundle?) {}
        override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
    })

    recognizer.startListening(intent)
}

private fun android.content.Context.sharePhoto(photo: GalleryPhoto) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = photo.mimeType.ifBlank { "image/*" }
        putExtra(Intent.EXTRA_STREAM, photo.uri)
        putExtra(Intent.EXTRA_TEXT, photo.displayName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(
        Intent.createChooser(shareIntent, "Share memory")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    )
}
