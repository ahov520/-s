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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.readflow.app.domain.model.Book
import com.readflow.app.ui.shelf.components.BookCard
import com.readflow.app.ui.shelf.components.EmptyState
import com.readflow.app.ui.theme.ZenithAccent
import com.readflow.app.ui.theme.ZenithAccentSoft
import com.readflow.app.ui.theme.ZenithBackground
import coil.compose.AsyncImage

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
    var pendingDelete by remember { mutableStateOf<Book?>(null) }
    var activeTab by rememberSaveable { mutableStateOf(ShelfTab.LIBRARY) }
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
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
                    isSearching = isSearching,
                    searchQuery = searchQuery,
                    onSearchModeChange = { isSearching = it },
                    onSearchQueryChange = { searchQuery = it },
                    onImport = { launcher.launch(arrayOf("text/plain", "text/*")) },
                    onOpenBook = { onOpenReader(it.id) },
                    onLongPressBook = { pendingDelete = it },
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
                    onImport = { launcher.launch(arrayOf("text/plain", "text/*")) },
                )
            }

            if (uiState.isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }

        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除书籍") },
                text = { Text("确认从书架移除《${pendingDelete?.title}》吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBook(pendingDelete!!.id)
                            pendingDelete = null
                        }
                    ) {
                        Text("删除")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("取消")
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
    isSearching: Boolean,
    searchQuery: String,
    onSearchModeChange: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onImport: () -> Unit,
    onOpenBook: (Book) -> Unit,
    onLongPressBook: (Book) -> Unit,
) {
    val filteredBooks = remember(books, searchQuery) {
        if (searchQuery.isBlank()) books else books.filter {
            it.title.contains(searchQuery, ignoreCase = true)
        }
    }
    val continueBook = books.firstOrNull { it.progress in 0.001f..0.999f }

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
        GridSection(books = books, onOpenBook = onOpenBook, onLongPressBook = onLongPressBook)
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
    onImport: () -> Unit,
) {
    val total = books.size
    val reading = books.count { it.progress in 0.001f..0.999f }
    val finished = books.count { it.progress >= 0.999f }
    val latest = books.maxByOrNull { it.updatedAt }?.title ?: "暂无"

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
