package com.nattype.tester.ui.result

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nattype.tester.stun.*
import com.nattype.tester.viewmodel.NatTestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: NatTestViewModel,
    onBackToHome: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val result = state.result
    val context = LocalContext.current

    // 启动测试（从 Home 导航过来时）
    LaunchedEffect(Unit) {
        if (!state.isTesting && state.result == null) {
            viewModel.startTest()
        }
    }

    // 自动滚动日志
    val listState = rememberLazyListState()
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    // 离开页面时自动取消检测，避免中间状态闪现（先导航后清理）
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelTest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isTesting) "正在检测" else "检测结果") },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (result != null && !state.isTesting) {
                        IconButton(onClick = {
                            viewModel.reset()
                            viewModel.startTest()
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "重新检测")
                        }
                        IconButton(onClick = {
                            copyResult(context, result)
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "分享")
                        }
                    }
                    if (state.isTesting) {
                        IconButton(onClick = onBackToHome) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            // 正在测试中：显示进度 UI
            state.isTesting -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // 当前步骤提示
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                state.currentStep,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 日志区域
                    if (state.logs.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "检测日志",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(state.logs) { log ->
                            val color = when {
                                log.startsWith("❌") || log.contains("失败") || log.contains("异常") || log.contains("错误") -> MaterialTheme.colorScheme.error
                                log.startsWith("✅") -> MaterialTheme.colorScheme.primary
                                log.startsWith("===") -> MaterialTheme.colorScheme.tertiary
                                log.startsWith("---") -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Text(
                                log,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = color,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // 有结果：显示结果
            result != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 错误信息（置于顶部）
                    if (state.errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    state.errorMessage!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // NAT 类型结果卡片
                    NatTypeResultCard(natType = result.natType)

                    // 映射行为（移到详细信息上面）
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AltRoute, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "映射行为",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${result.mappingBehavior.displayName} (${result.mappingBehavior.abbreviation})",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                result.mappingBehavior.englishName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                result.mappingBehavior.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 过滤行为（移到详细信息上面）
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FilterAlt, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "过滤行为",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "${result.filteringBehavior.displayName} (${result.filteringBehavior.abbreviation})",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                result.filteringBehavior.englishName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                result.filteringBehavior.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 详细信息（可折叠，默认折叠）
                    var detailExpanded by remember { mutableStateOf(false) }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { detailExpanded = !detailExpanded }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "详细信息",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    if (detailExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (detailExpanded) "收起" else "展开",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            AnimatedVisibility(visible = detailExpanded) {
                                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                                    DetailRow("映射地址", result.publicAddress?.hostString ?: "N/A")
                                    DetailRow("映射端口", result.publicAddress?.port?.toString() ?: "N/A")
                                    DetailRow("本地地址", result.localAddress?.hostString ?: "N/A")
                                    DetailRow("响应来源", result.responseOrigin?.hostString ?: "N/A")
                                    DetailRow("其他地址", result.otherAddress?.hostString ?: "N/A")
                                }
                            }
                        }
                    }

                    // 检测日志（结果页底部，可折叠，默认折叠）
                    var logsExpanded by remember { mutableStateOf(false) }
                    if (result.logs.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { logsExpanded = !logsExpanded }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "检测日志",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        if (logsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (logsExpanded) "收起" else "展开",
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                AnimatedVisibility(visible = logsExpanded) {
                                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                                        result.logs.forEach { log ->
                                            val color = when {
                                                log.startsWith("❌") || log.contains("失败") || log.contains("异常") || log.contains("错误") -> MaterialTheme.colorScheme.error
                                                log.startsWith("✅") -> Color(0xFF4CAF50)
                                                log.startsWith("===") -> MaterialTheme.colorScheme.tertiary
                                                log.startsWith("---") -> MaterialTheme.colorScheme.onSurfaceVariant
                                                log.contains("⚠️") -> Color(0xFFFF9800)
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                            Text(
                                                log,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                ),
                                                color = color,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                        // 复制日志按钮（右下角）
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            TextButton(onClick = {
                                                copyLogs(context, result.logs)
                                                Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                            }) {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("复制日志", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }

            // 无结果且不在测试：等待开始或显示错误
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 错误信息
                    if (state.errorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    state.errorMessage!!,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    // 等待开始
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("准备检测...", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NatTypeResultCard(natType: NatType) {
    val (bgColor, icon) = when (natType) {
        NatType.FULL_CONE -> Pair(Color(0xFF4CAF50), Icons.Default.CheckCircle)
        NatType.RESTRICTED_CONE -> Pair(Color(0xFFFFC107), Icons.Default.Shield)
        NatType.PORT_RESTRICTED_CONE -> Pair(Color(0xFFFF9800), Icons.Default.Security)
        NatType.SYMMETRIC -> Pair(Color(0xFFF44336), Icons.Default.Lock)
        NatType.UDP_BLOCKED -> Pair(Color(0xFF9E9E9E), Icons.Default.Block)
        NatType.OTHER -> Pair(Color(0xFF78909C), Icons.Default.Info)
        NatType.UNKNOWN -> Pair(Color(0xFF607D8B), Icons.AutoMirrored.Filled.Help)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标
            Icon(
                icon,
                contentDescription = null,
                tint = bgColor,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            // 右侧文字
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    natType.shortName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = bgColor
                )
                Text(
                    natType.englishName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    natType.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.Medium,
            softWrap = true,
            overflow = TextOverflow.Visible
        )
    }
}

private fun copyResult(context: Context, result: StunResult) {
    val text = buildString {
        appendLine("=== NAT 类型检测结果 ===")
        appendLine("NAT 类型: ${result.natType.shortName} (${result.natType.displayName})")
        appendLine("映射行为: ${result.mappingBehavior.displayName}")
        appendLine("过滤行为: ${result.filteringBehavior.displayName}")
        appendLine("公网地址: ${result.publicAddress?.hostString ?: "N/A"}:${result.publicAddress?.port ?: ""}")
        appendLine("本地地址: ${result.localAddress?.hostString ?: "N/A"}")
        appendLine()
        appendLine("--- NatTypeTester ---")
    }
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NAT 检测结果", text))
}

private fun copyLogs(context: Context, logs: List<String>) {
    val text = logs.joinToString("\n")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("NAT 检测日志", text))
}
