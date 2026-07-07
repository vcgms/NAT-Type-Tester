package com.nattype.tester.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nattype.tester.BuildConfig
import com.nattype.tester.network.StunServer
import com.nattype.tester.viewmodel.NatTestViewModel
import com.nattype.tester.viewmodel.TransportProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: NatTestViewModel,
    onStartTest: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    var serverDropdownExpanded by remember { mutableStateOf(false) }
    var protocolDropdownExpanded by remember { mutableStateOf(false) }

    // 自定义服务器输入文本
    var serverText by remember { mutableStateOf("${state.selectedServer.host}:${state.selectedServer.port}") }
    LaunchedEffect(state.selectedServer) {
        serverText = "${state.selectedServer.host}:${state.selectedServer.port}"
    }

    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("NAT 类型检测工具")
                            Text(
                                "NAT Type Tester",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 网络信息卡片
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 本地地址
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "本地地址",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    ClickableAddressLine(state.localIPv4 ?: "获取中...")
                    state.localIPv6?.let { ClickableAddressLine(it) }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 公网地址
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "公网地址",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    ClickableAddressLine(state.publicIPv4 ?: "获取中...")
                    state.publicIPv6?.let { ClickableAddressLine(it) }
                }
            }

            // STUN 服务器选择（下拉菜单）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "STUN 服务器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = serverDropdownExpanded,
                        onExpandedChange = {
                            serverDropdownExpanded = it
                            if (!it) focusManager.clearFocus()
                        }
                    ) {
                        OutlinedTextField(
                            value = serverText,
                            onValueChange = { serverText = it },
                            singleLine = true,
                            label = { Text("主机:端口") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverDropdownExpanded) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = serverDropdownExpanded,
                            onDismissRequest = { serverDropdownExpanded = false }
                        ) {
                            StunServer.DEFAULT_SERVERS.forEach { server ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(server.label, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                "${server.host}:${server.port}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        serverText = "${server.host}:${server.port}"
                                        viewModel.selectServer(server)
                                        serverDropdownExpanded = false
                                        focusManager.clearFocus()
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            // 传输协议选择（下拉菜单）
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cable, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "传输协议",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = protocolDropdownExpanded,
                        onExpandedChange = { protocolDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = state.selectedProtocol.label,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = protocolDropdownExpanded,
                            onDismissRequest = { protocolDropdownExpanded = false }
                        ) {
                            TransportProtocol.entries.forEach { protocol ->
                                DropdownMenuItem(
                                    text = { Text(protocol.label) },
                                    onClick = {
                                        viewModel.selectProtocol(protocol)
                                        protocolDropdownExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            // 开始测试 / 查看结果按钮
            Button(
                onClick = {
                    val presetText = "${state.selectedServer.host}:${state.selectedServer.port}"
                    if (serverText != presetText) {
                        viewModel.setCustomServer(serverText)
                    }
                    onStartTest()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !state.isTesting
            ) {
                if (state.result != null) {
                    Icon(Icons.Default.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查看检测结果", style = MaterialTheme.typography.titleMedium)
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始检测", style = MaterialTheme.typography.titleMedium)
                }
            }

            // 版本号
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ClickableAddressLine(value: String) {
    val context = LocalContext.current
    Text(
        value,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace
        ),
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("IP", value))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
    )
}
