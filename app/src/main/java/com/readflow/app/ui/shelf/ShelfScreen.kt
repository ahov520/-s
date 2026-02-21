package com.readflow.app.ui.shelf

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readflow.app.domain.model.Book
import com.readflow.app.domain.usecase.BackupAndSyncUseCase
import com.readflow.app.ui.shelf.components.BookCard
import com.readflow.app.ui.shelf.components.EmptyState
import com.readflow.app.ui.theme.ZenithAccent
import com.readflow.app.ui.theme.ZenithAccentSoft
import com.readflow.app.ui.theme.ZenithBackground
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ShelfTab {
    LIBRARY,
    DISCOVER,
    PROFILE,
}

@Composable
fun ShelfScreen(
    onOpenReader: (String) -> Unit,
    viewModel: ShelfViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingBookAction by remember { mutableStateOf<Book?>(null) }
    var activeTab by rememberSaveable { mutableStateOf(ShelfTab.LIBRARY) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var libraryGroupFilter by rememberSaveable { mutableStateOf("全部") }
    var discoverCategory by rememberSaveable { mutableStateOf("全部") }
    var showDiscoverFilter by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) viewModel.importBook(uri) }
    )

    Scaffold(
        containerColor = ZenithBackground,
        bottomBar = {
            ShelfBottomBar(
                activeTab = activeTab,
                onTabSelected = {
                    activeTab = it
                    isSearching = false
                    searchQuery = ""
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                ShelfTab.LIBRARY -> LibraryTab(
                    books = uiState.books,
                    bookGroups = uiState.bookGroups,
                    isSearching = isSearching,
                    searchQuery = searchQuery,
                    selectedGroup = libraryGroupFilter,
                    onGroupSelected = { libraryGroupFilter = it },
                    onSearchModeChange = { isSearching = it },
                    onSearchQueryChange = { searchQuery = it },
                    onImport = { launcher.launch(arrayOf("text/plain", "text/*")) },
                    onOpenBook = { onOpenReader(it.id) },
                    onLongPressBook = { pendingBookAction = it },
                )

                ShelfTab.DISCOVER -> DiscoverTab(
                    books = uiState.books,
                    isSearching = isSearching,
                    searchQuery = searchQuery,
                    selectedCategory = discoverCategory,
                    onSearchModeChange = { isSearching = it },
                    onSearchQueryChange = { searchQuery = it },
                    onCategorySelected = { discoverCategory = it },
                    onShowFilter = { showDiscoverFilter = true },
                    onOpenBook = { onOpenReader(it.id) },
                )

                ShelfTab.PROFILE -> ProfileTab(
                    books = uiState.books,
                    dailyReadSeconds = uiState.dailyReadSeconds,
                    streakDays = uiState.streakDays,
                    dailyGoalMinutes = uiState.dailyGoalMinutes,
                    reminderEnabled = uiState.reminderEnabled,
                    reminderHour = uiState.reminderHour,
                    reminderMinute = uiState.reminderMinute,
                    notesCount = uiState.notesCount,
                    cloudSyncToken = uiState.cloudSyncToken,
                    cloudGistId = uiState.cloudGistId,
                    lastBackupPath = uiState.lastBackupPath,
                    lastSyncAt = uiState.lastSyncAt,
                    restoreMode = uiState.restoreMode,
                    isWorking = uiState.isWorking,
                    workLabel = uiState.workLabel,
                    onImport = { launcher.launch(arrayOf("text/plain", "text/*")) },
                    onGoalChange = viewModel::updateDailyGoalMinutes,
                    onReminderChange = viewModel::updateReminder,
                    onRestoreModeChange = viewModel::updateRestoreMode,
                    onCloudConfigChange = viewModel::updateCloudSyncConfig,
                    onBackupLocal = viewModel::backupToLocal,
                    onRestoreLocal = viewModel::restoreFromLocalBackup,
                    onSyncUp = viewModel::syncToCloud,
                    onSyncDown = viewModel::restoreFromCloud,
                )
            }

            if (uiState.isImporting || uiState.isWorking) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        if (uiState.error != null || uiState.message != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearFeedback() },
                title = { Text(if (uiState.error != null) "操作失败" else "操作结果") },
                text = { Text(uiState.error ?: uiState.message.orEmpty()) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearFeedback() }) { Text("知道了") }
                },
            )
        }

        if (pendingBookAction != null) {
            AlertDialog(
                onDismissRequest = { pendingBookAction = null },
                title = { Text("书籍操作") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("《${pendingBookAction?.title}》")
                        Text("选择分组")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("未分组", "收藏", "追更", "完结").forEach { group ->
                                FilterChip(
                                    selected = if (group == "未分组") {
                                        uiState.bookGroups[pendingBookAction?.id.orEmpty()].isNullOrBlank()
                                    } else {
                                        uiState.bookGroups[pendingBookAction?.id.orEmpty()] == group
                                    },
                                    onClick = {
                                        pendingBookAction?.let { viewModel.updateBookGroup(it.id, group) }
                                    },
                                    label = { Text(group) },
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingBookAction?.let { viewModel.deleteBook(it.id) }
                            pendingBookAction = null
                        }
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingBookAction = null }) {
                        Text("完成")
                    }
                }
            )
        }

        if (showDiscoverFilter) {
            DiscoverFilterSheet(
                selectedCategory = discoverCategory,
                onDismiss = { showDiscoverFilter = false },
                onCategorySelected = {
                    discoverCategory = it
                    showDiscoverFilter = false
                },
            )
        }
    }
}

@Composable
private fun LibraryTab(
    books: List<Book>,
    bookGroups: Map<String, String>,
    isSearching: Boolean,
    searchQuery: String,
    selectedGroup: String,
    onGroupSelected: (String) -> Unit,
    onSearchModeChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onLongPressBook: (Book) -> Unit,
) {
    val filteredBooks = remember(books, bookGroups, selectedGroup, searchQuery) {
        books.filter { book ->
            val matchesGroup = when (selectedGroup) {
                "全部" -> true
                "未分组" -> bookGroups[book.id].isNullOrBlank()
                else -> bookGroups[book.id] == selectedGroup
            }
            val matchesSearch = searchQuery.isBlank() || book.title.contains(searchQuery, ignoreCase = true)
            matchesGroup && matchesSearch
        }
    }
    val continueBook = books.firstOrNull { it.progress in 0.001f..0.999f }
    val groupOptions = remember(bookGroups) {
        listOf("全部", "未分组") + bookGroups.values.filter { it.isNotBlank() }.distinct()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShelfHeader(
            title = "书架",
            isSearching = isSearching,
            searchQuery = searchQuery,
            searchPlaceholder = "搜索书架...",
            onSearchModeChange = onSearchModeChange,
            onSearchQueryChange = onSearchQueryChange,
            onPrimaryAction = onImport,
            primaryIcon = Icons.Default.Add,
            primaryLabel = "导入",
        )

        if (books.isEmpty()) {
            EmptyState()
            return@Column
        }

        if (isSearching) {
            Text(
                text = if (searchQuery.isBlank()) "全部书籍" else "搜索结果（${filteredBooks.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GridSection(books = filteredBooks, onOpenBook = onOpenBook, onLongPressBook = onLongPressBook)
            return@Column
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            groupOptions.forEach { group ->
                FilterChip(
                    selected = selectedGroup == group,
                    onClick = { onGroupSelected(group) },
                    label = { Text(group) },
                )
            }
        }

        AnimatedVisibility(visible = continueBook != null) {
            continueBook?.let { book ->
                ContinueReadingCard(book = book, onClick = { onOpenBook(book) })
            }
        }

        Text(
            text = "全部书籍",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GridSection(books = filteredBooks, onOpenBook = onOpenBook, onLongPressBook = onLongPressBook)
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun DiscoverTab(
    books: List<Book>,
    isSearching: Boolean,
    searchQuery: String,
    selectedCategory: String,
    onSearchModeChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String) -> Unit,
    onShowFilter: () -> Unit,
    onOpenBook: (Book) -> Unit,
) {
    val categories = listOf("全部", "推荐", "玄幻", "科幻", "言情", "悬疑", "历史", "武侠")
    val booksByCategory = remember(books, selectedCategory) {
        filterBooksByCategory(books, selectedCategory)
    }
    val filteredBooks = remember(booksByCategory, searchQuery) {
        if (searchQuery.isBlank()) booksByCategory else booksByCategory.filter {
            it.title.contains(searchQuery, ignoreCase = true)
        }
    }
    val featured = filteredBooks.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ShelfHeader(
            title = "发现",
            isSearching = isSearching,
            searchQuery = searchQuery,
            searchPlaceholder = "搜索发现...",
            onSearchModeChange = onSearchModeChange,
            onSearchQueryChange = onSearchQueryChange,
            onPrimaryAction = onShowFilter,
            primaryIcon = Icons.Default.Tune,
            primaryLabel = "筛选",
        )

        if (isSearching) {
            Text(
                text = if (searchQuery.isBlank()) "全部书籍" else "搜索结果（${filteredBooks.size}）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GridSection(books = filteredBooks, onOpenBook = onOpenBook, onLongPressBook = { _ -> })
            return@Column
        }

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { label ->
                val selected = label == selectedCategory
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.White,
                    onClick = { onCategorySelected(label) },
                    modifier = Modifier.clip(RoundedCornerShape(999.dp))
                ) {
                    Text(
                        text = label,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        featured?.let { book ->
            FeaturedCard(book = book, onClick = { onOpenBook(book) })
        }

        if (filteredBooks.isEmpty()) {
            Text(
                text = "当前分类暂无书籍",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            text = "本周热门",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            filteredBooks.drop(1).ifEmpty { filteredBooks }.forEach { book ->
                Column(
                    modifier = Modifier
                        .width(132.dp)
                        .clickable { onOpenBook(book) },
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BookCard(
                        book = book,
                        onClick = { onOpenBook(book) },
                        onLongClick = {},
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun ProfileTab(
    books: List<Book>,
    dailyReadSeconds: Int,
    streakDays: Int,
    dailyGoalMinutes: Int,
    reminderEnabled: Boolean,
    reminderHour: Int,
    reminderMinute: Int,
    notesCount: Int,
    cloudSyncToken: String,
    cloudGistId: String,
    lastBackupPath: String,
    lastSyncAt: Long,
    restoreMode: BackupAndSyncUseCase.RestoreMode,
    isWorking: Boolean,
    workLabel: String?,
    onImport: () -> Unit,
    onGoalChange: (Int) -> Unit,
    onReminderChange: (Boolean, Int, Int) -> Unit,
    onRestoreModeChange: (BackupAndSyncUseCase.RestoreMode) -> Unit,
    onCloudConfigChange: (String, String) -> Unit,
    onBackupLocal: () -> Unit,
    onRestoreLocal: (BackupAndSyncUseCase.RestoreMode) -> Unit,
    onSyncUp: () -> Unit,
    onSyncDown: (BackupAndSyncUseCase.RestoreMode) -> Unit,
) {
    val total = books.size
    val reading = books.count { it.progress in 0.001f..0.999f }
    val finished = books.count { it.progress >= 0.999f }
    val latest = books.maxByOrNull { it.updatedAt }?.title ?: "暂无"
    val goalSeconds = (dailyGoalMinutes.coerceAtLeast(1) * 60)
    val goalProgress = (dailyReadSeconds.toFloat() / goalSeconds).coerceIn(0f, 1f)
    var tokenDraft by rememberSaveable { mutableStateOf(cloudSyncToken) }
    var gistDraft by rememberSaveable { mutableStateOf(cloudGistId) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(cloudSyncToken, cloudGistId) {
        tokenDraft = cloudSyncToken
        gistDraft = cloudGistId
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("阅读概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("书架总数：$total 本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("正在阅读：$reading 本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("已读完成：$finished 本", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("今日阅读：${formatReadingDuration(dailyReadSeconds)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("连续阅读：$streakDays 天", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("今日目标：$dailyGoalMinutes 分钟（${(goalProgress * 100).toInt()}%）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFE9EDF5))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(goalProgress)
                            .height(8.dp)
                            .background(ZenithAccent)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60, 90, 120).forEach { candidate ->
                        FilterChip(
                            selected = dailyGoalMinutes == candidate,
                            onClick = { onGoalChange(candidate) },
                            label = { Text("${candidate}分") },
                        )
                    }
                }
                Text("最近更新：$latest", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("快捷操作", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "导入新书",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onImport() }
                        .background(ZenithAccentSoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
                Text(
                    text = "说明：长按书籍可删除，阅读页可添加书签与切换主题。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("提醒与统计", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("每日提醒（${reminderHour.toString().padStart(2, '0')}:${reminderMinute.toString().padStart(2, '0')}）")
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { onReminderChange(it, reminderHour, reminderMinute) },
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(8, 12, 18, 21).forEach { hour ->
                        FilterChip(
                            selected = reminderHour == hour,
                            onClick = { onReminderChange(reminderEnabled, hour, 0) },
                            label = { Text("${hour}:00") },
                        )
                    }
                }
                Text("累计笔记：$notesCount 条", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("备份与云同步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = restoreMode == BackupAndSyncUseCase.RestoreMode.MERGE,
                        onClick = { onRestoreModeChange(BackupAndSyncUseCase.RestoreMode.MERGE) },
                        label = { Text("合并恢复") },
                    )
                    FilterChip(
                        selected = restoreMode == BackupAndSyncUseCase.RestoreMode.OVERWRITE,
                        onClick = { onRestoreModeChange(BackupAndSyncUseCase.RestoreMode.OVERWRITE) },
                        label = { Text("覆盖恢复") },
                    )
                }
                OutlinedTextField(
                    value = tokenDraft,
                    onValueChange = { tokenDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("GitHub Token") },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(
                                imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showToken) "隐藏 Token" else "显示 Token",
                            )
                        }
                    },
                )
                OutlinedTextField(
                    value = gistDraft,
                    onValueChange = { gistDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Gist ID（首次可留空）") },
                )
                Text(
                    text = "保存云配置",
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isWorking) { onCloudConfigChange(tokenDraft, gistDraft) }
                        .background(if (isWorking) Color(0xFFE7EAF0) else ZenithAccentSoft)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    color = if (isWorking) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTextButton(
                        text = "本地备份",
                        onClick = onBackupLocal,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTextButton(
                        text = "本地恢复",
                        onClick = { onRestoreLocal(restoreMode) },
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionTextButton(
                        text = "上传云端",
                        onClick = onSyncUp,
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                    )
                    ActionTextButton(
                        text = "云端恢复",
                        onClick = { onSyncDown(restoreMode) },
                        enabled = !isWorking,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isWorking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = workLabel ?: "正在处理，请稍候...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "最近备份：${if (lastBackupPath.isBlank()) "暂无" else lastBackupPath}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "最近云同步：${formatSyncTime(lastSyncAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShelfHeader(
    title: String,
    isSearching: Boolean,
    searchQuery: String,
    searchPlaceholder: String,
    onSearchModeChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector,
    primaryLabel: String,
) {
    AnimatedVisibility(visible = isSearching) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                shape = RoundedCornerShape(16.dp),
            )
            IconButton(
                onClick = {
                    onSearchModeChange(false)
                    onSearchQueryChange("")
                }
            ) {
                Icon(Icons.Default.Close, contentDescription = "关闭搜索")
            }
        }
    }

    AnimatedVisibility(visible = !isSearching) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircleActionButton(onClick = { onSearchModeChange(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "搜索")
                }
                CircleActionButton(onClick = onPrimaryAction) {
                    Icon(primaryIcon, contentDescription = primaryLabel)
                }
            }
        }
    }
}

@Composable
private fun CircleActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 4.dp,
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
private fun ContinueReadingCard(
    book: Book,
    onClick: () -> Unit,
) {
    val progress = (book.progress.coerceIn(0f, 1f) * 100).toInt()
    val cover = runCatching { Color(android.graphics.Color.parseColor(book.coverColor)) }
        .getOrElse { ZenithAccent }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                if (book.coverImageUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(cover.copy(alpha = 0.95f), cover.copy(alpha = 0.65f))
                                )
                            )
                    )
                } else {
                    AsyncImage(
                        model = book.coverImageUrl,
                        contentDescription = "${book.title}封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "继续阅读",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFEEF2F7))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.progress.coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(ZenithAccent)
                    )
                }
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.labelMedium,
                    color = ZenithAccent,
                )
            }
        }
    }
}

@Composable
private fun FeaturedCard(
    book: Book,
    onClick: () -> Unit,
) {
    val cover = runCatching { Color(android.graphics.Color.parseColor(book.coverColor)) }
        .getOrElse { ZenithAccent }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 10.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(18.dp)
        ) {
            if (book.coverImageUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    cover.copy(alpha = 0.5f),
                                    cover.copy(alpha = 0.9f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
            } else {
                AsyncImage(
                    model = book.coverImageUrl,
                    contentDescription = "${book.title}封面",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Black.copy(alpha = 0.8f),
                                )
                            )
                        )
                )
            }
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFFB923C).copy(alpha = 0.9f),
                ) {
                    Text(
                        text = "热门推荐",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = book.title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "打开继续阅读",
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GridSection(
    books: List<Book>,
    onOpenBook: (Book) -> Unit,
    onLongPressBook: (Book) -> Unit,
) {
    if (books.isEmpty()) {
        Text(
            text = "暂无匹配书籍",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 32.dp)
        )
        return
    }

    BoxWithConstraints {
        val columns = if (maxWidth > 560.dp) 4 else if (maxWidth > 400.dp) 3 else 2
        val chunks = books.chunked(columns)

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            chunks.forEach { rowBooks ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowBooks.forEach { book ->
                        Box(modifier = Modifier.weight(1f)) {
                            BookCard(
                                book = book,
                                onClick = { onOpenBook(book) },
                                onLongClick = { onLongPressBook(book) },
                            )
                        }
                    }
                    repeat(columns - rowBooks.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DiscoverFilterSheet(
    selectedCategory: String,
    onDismiss: () -> Unit,
    onCategorySelected: (String) -> Unit,
) {
    val categories = listOf("全部", "推荐", "玄幻", "科幻", "言情", "悬疑", "历史", "武侠")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("筛选分类", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = category == selectedCategory,
                        onClick = { onCategorySelected(category) },
                        label = { Text(category) },
                    )
                }
            }
        }
    }
}

private fun filterBooksByCategory(books: List<Book>, category: String): List<Book> {
    if (category == "全部") return books
    if (category == "推荐") return books.sortedByDescending { it.updatedAt }
    return books.filter { classifyCategoryByTitle(it.title) == category }
}

private fun classifyCategoryByTitle(title: String): String {
    val lower = title.lowercase()
    return when {
        lower.contains("仙") || lower.contains("玄") || lower.contains("魔") -> "玄幻"
        lower.contains("星") || lower.contains("机甲") || lower.contains("科幻") -> "科幻"
        lower.contains("爱") || lower.contains("恋") || lower.contains("总裁") -> "言情"
        lower.contains("案") || lower.contains("罪") || lower.contains("谜") -> "悬疑"
        lower.contains("史") || lower.contains("朝") || lower.contains("国") -> "历史"
        lower.contains("侠") || lower.contains("江湖") || lower.contains("剑") -> "武侠"
        else -> "推荐"
    }
}

@Composable
private fun ShelfBottomBar(
    activeTab: ShelfTab,
    onTabSelected: (ShelfTab) -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.96f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            BottomNavItem(
                icon = Icons.Default.Book,
                label = "书架",
                active = activeTab == ShelfTab.LIBRARY,
                onClick = { onTabSelected(ShelfTab.LIBRARY) },
            )
            BottomNavItem(
                icon = Icons.Default.Explore,
                label = "发现",
                active = activeTab == ShelfTab.DISCOVER,
                onClick = { onTabSelected(ShelfTab.DISCOVER) },
            )
            BottomNavItem(
                icon = Icons.Default.Person,
                label = "我的",
                active = activeTab == ShelfTab.PROFILE,
                onClick = { onTabSelected(ShelfTab.PROFILE) },
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val color = if (active) ZenithAccent else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (active) ZenithAccentSoft else Color.Transparent)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Icon(icon, contentDescription = label, tint = color)
        }
        Text(text = label, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ActionTextButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .background(if (enabled) ZenithAccentSoft else Color(0xFFE7EAF0))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun formatSyncTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return "暂无"
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

private fun formatReadingDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}小时${m}分钟" else "${m}分钟"
}
