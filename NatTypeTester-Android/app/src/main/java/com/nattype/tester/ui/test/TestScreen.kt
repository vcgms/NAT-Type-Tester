package com.nattype.tester.ui.test

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nattype.tester.viewmodel.NatTestViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    viewModel: NatTestViewModel,
    onTestComplete: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // 启动测试
    LaunchedEffect(Unit) {
        viewModel.startTest()
    }

    // 检测完成自动跳转
    LaunchedEffect(state.result, state.isTesting) {
        if (!state.isTesting && state.result != null) {
            onTestComplete()
        }
    }

    val listState = rememberLazyListState()

    // 自动滚动到最新日志
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("正在检测") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelTest()
                        onNavigateBack()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isTesting) {
                        IconButton(onClick = { viewModel.cancelTest() }) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 进度区域
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            strokeWidth = 6.dp
                        )
                    } else if (state.result?.isSuccess == true) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )
                    } else {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        state.currentStep,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // 日志区域
            Text(
                "检测日志",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

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
                        log.startsWith("✅") -> MaterialTheme.colorScheme.primary
                        log.startsWith("❌") -> MaterialTheme.colorScheme.error
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
}
