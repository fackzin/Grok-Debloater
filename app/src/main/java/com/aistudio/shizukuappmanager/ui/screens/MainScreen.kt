package com.aistudio.shizukuappmanager.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.aistudio.shizukuappmanager.data.model.AppInfo
import com.aistudio.shizukuappmanager.ui.viewmodel.AppFilter
import com.aistudio.shizukuappmanager.ui.viewmodel.AppSafety
import com.aistudio.shizukuappmanager.ui.viewmodel.AppSortOrder
import com.aistudio.shizukuappmanager.ui.viewmodel.AppViewModel
import com.aistudio.shizukuappmanager.ui.viewmodel.DeviceStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val isShizukuInstalled by viewModel.isShizukuInstalled.collectAsState()
    val isShizukuActive by viewModel.isShizukuActive.collectAsState()
    val isShizukuPermissionGranted by viewModel.isShizukuPermissionGranted.collectAsState()

    val appsList by viewModel.filteredApps.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedPackages by viewModel.selectedPackages.collectAsState()
    val deviceStats by viewModel.deviceStats.collectAsState()

    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val processingTitle by viewModel.processingTitle.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val showConfirmationDialog by viewModel.showConfirmationDialog.collectAsState()

    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Shizuku App Manager",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (selectedPackages.isNotEmpty()) {
                            Text(
                                text = "${selectedPackages.size} selecionado(s)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                },
                actions = {
                    // Botão Recarregar lista
                    IconButton(
                        onClick = { viewModel.loadAppsList() },
                        modifier = Modifier.testTag("appbar_reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Recarregar"
                        )
                    }

                    if (selectedPackages.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("appbar_clear_selection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpar Seleção"
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.selectAllInCurrentFilter() },
                        modifier = Modifier.testTag("appbar_select_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selecionar Todos"
                        )
                    }

                    // Botão de mais opções (Exportar, Estatísticas)
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Mais opções"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Estatísticas do Dispositivo") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showStatsDialog = true
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Exportar como TXT (Copiar)") },
                                leadingIcon = { Icon(Icons.Default.Menu, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.exportFilteredAppsList("txt")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar como CSV (Copiar)") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.exportFilteredAppsList("csv")
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedPackages.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                val isRestoreMode = selectedFilter == AppFilter.UNINSTALLED
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Ações para ${selectedPackages.size} app(s)",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isRestoreMode) "Isso instalará/restaurará os selecionados." else "Isso desinstalará permanentemente os selecionados.",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        if (isRestoreMode) {
                            Button(
                                onClick = { viewModel.startRestoreSelectedApps() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF15803D), // Verde Seguro para restauração
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("bulk_restore_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Restaurar",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Restaurar")
                                }
                            }
                        } else {
                            Button(
                                onClick = { viewModel.triggerUninstallConfirmation(true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("bulk_uninstall_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Desinstalar",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Desinstalar")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Shizuku Service Status Indicator Card
            ShizukuStatusCard(
                installed = isShizukuInstalled,
                active = isShizukuActive,
                permissionGranted = isShizukuPermissionGranted,
                onRequestPermission = { viewModel.requestShizukuPermission() },
                onRefreshStatus = { viewModel.checkShizukuStatus() }
            )

            // 2. HUD Compacto para Estatísticas Rápidas do Dispositivo
            DeviceStatsMiniHud(stats = deviceStats, onShowFullStats = { showStatsDialog = true })

            // 3. Search, Filters & Sorting deck
            SearchFilterAndSortHeader(
                searchQuery = searchQuery,
                onSearchChanged = { viewModel.setSearchQuery(it) },
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) },
                sortOrder = sortOrder,
                onSortSelected = { viewModel.setSortOrder(it) }
            )

            // 4. Application List Layout
            if (isLoadingApps) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Buscando pacotes instalados...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            } else if (appsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Vazio",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (searchQuery.isNotEmpty()) "Nenhum aplicativo corresponde à sua pesquisa." else "Nenhum aplicativo listado neste filtro.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("app_list"),
                    contentPadding = PaddingValues(bottom = if (selectedPackages.isNotEmpty()) 100.dp else 24.dp)
                ) {
                    if (selectedFilter == AppFilter.UNINSTALLED) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Salvar & Compartilhar Lista",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Você pode salvar ou compartilhar um relatório limpo e organizado com toda a lista de seus aplicativos desinstalados e/ou backups locais para enviar para outra pessoa.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                        lineHeight = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.exportFilteredAppsList("txt") },
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1f).height(36.dp),
                                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 10.dp)
                                        ) {
                                            Text("Salvar Local", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = { viewModel.shareUninstalledAppsList(context) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.weight(1.2f).height(36.dp),
                                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Compartilhar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    items(items = appsList, key = { it.packageName }) { appInfo ->
                        val isSelected = selectedPackages.contains(appInfo.packageName)
                        AppItemCard(
                            appInfo = appInfo,
                            isSelected = isSelected,
                            safety = viewModel.getAppSafetyCategory(appInfo),
                            onToggleSelect = { viewModel.toggleAppSelection(appInfo.packageName) },
                            onBackupApk = { viewModel.backupSelectedApk(appInfo) }
                        )
                    }
                }
            }
        }
    }

    // 1. CONFIRMATION DELETION DIALOG WITH SAFEGUARDS
    if (showConfirmationDialog) {
        val selectedCount = selectedPackages.size
        val criticalSelected = appsList.filter { selectedPackages.contains(it.packageName) && viewModel.CRITICAL_SYSTEM_PACKAGES.contains(it.packageName) }
        
        AlertDialog(
            onDismissRequest = { viewModel.triggerUninstallConfirmation(false) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alerta",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(40.dp)
                )
            },
            title = {
                Text(
                    text = "Aviso Crítico de Desinstalação",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Você tem certeza de que deseja desinstalar os $selectedCount aplicativo(s) selecionados via processo privilegiado do Shizuku?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (criticalSelected.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "ATENÇÃO: Você selecionou pacotes críticos do sistema que são protegidos contra desinstalação. O assistente de segurança irá saltar estes itens automaticamente para evitar que você danifique o seu aparelho.",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Aviso para Apps de Fábrica/Sistema: Eles serão removidos no perfil padrão (usuário 0). Se você desinstalar uma biblioteca ou gerenciador de tela necessário para o sistema operacional, o telefone poderá ficar instável.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        LazyColumn(modifier = Modifier.padding(10.dp)) {
                            val itemsToShow = appsList.filter { selectedPackages.contains(it.packageName) }
                            items(itemsToShow) { item ->
                                val isProtected = viewModel.CRITICAL_SYSTEM_PACKAGES.contains(item.packageName)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "• ${item.appName}",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isProtected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = if (isProtected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isProtected) {
                                        Text(
                                            text = "[PROTEGIDO]",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.startUninstallSelectedApps() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_uninstall_button")
                ) {
                    Text("Confirmar Desinstalação", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.triggerUninstallConfirmation(false) }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // 2. DETAILED DEVICE STATISTICS DIALOG
    if (showStatsDialog) {
        DeviceStatisticsDialog(
            stats = deviceStats,
            onDismiss = { showStatsDialog = false }
        )
    }

    // 3. SECURE PROCESSING LAUNCH OVERLAY
    if (isProcessing) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        progress = { processingProgress },
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = processingTitle,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Por favor, aguarde. Os pacotes estão sendo desinstalados de forma sequencial e o cache está sendo limpo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { processingProgress },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape)
                    )
                }
            }
        }
    }

    // 4. ACTION OUTPUT MESSAGE BOX
    if (operationMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissOperationMessage() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Resultado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Resultado da Operação",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            Text(
                                text = operationMessage ?: "",
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissOperationMessage() },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("dismiss_result_button")
                ) {
                    Text("Fechar")
                }
            }
        )
    }
}

/**
 * Representa os estados de alerta e ativação do Shizuku.
 */
@Composable
fun ShizukuStatusCard(
    installed: Boolean,
    active: Boolean,
    permissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    val dotColor = when {
        !installed -> MaterialTheme.colorScheme.error
        !active -> Color(0xFFF59E0B) // Amber
        !permissionGranted -> Color(0xFF3B82F6) // Blue
        else -> Color(0xFF22C55E) // Green (Ready)
    }

    val titleText = when {
        !installed -> "Shizuku não Detectado"
        !active -> "Shizuku Desativado"
        !permissionGranted -> "Acesso não Autorizado"
        else -> "Shizuku Ativo"
    }

    val subtitleText = when {
        !installed -> "Instale o app Shizuku oficial"
        !active -> "Serviço dependente em segundo plano inativo"
        !permissionGranted -> "Toque para conceder as credenciais"
        else -> "Canal de comunicação seguro ativo"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Status indicator
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(dotColor.copy(alpha = 0.35f), CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(dotColor, CircleShape)
                    )
                }

                Column {
                    Text(
                        text = titleText,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (installed && active && !permissionGranted) {
                    TextButton(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("request_permission_button")
                    ) {
                        Text(
                            text = "CONFIGURAR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    IconButton(
                        onClick = onRefreshStatus,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reverificar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * HUD Compacto de Estatísticas Rápidas
 */
@Composable
fun DeviceStatsMiniHud(
    stats: DeviceStats,
    onShowFullStats: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable { onShowFullStats() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Aparelho possui ${stats.totalApps} apps (${stats.recommendedApps} Rec. • ${stats.optionalApps} Opc. • ${stats.dangerousApps} Perigosos)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Ver",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Filtro, Pesquisa e Ordenação (Painel Único de Categorias, Sem Rolagem Necessária)
 */
@Composable
fun SearchFilterAndSortHeader(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    selectedFilter: AppFilter,
    onFilterSelected: (AppFilter) -> Unit,
    sortOrder: AppSortOrder,
    onSortSelected: (AppSortOrder) -> Unit
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Nome, parte do pacote...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisa") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChanged("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpar")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Painel Consolidado de Filtros de Âmbito e Segurança (Lugar Único)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Seção 1: Âmbito / Status do Aplicativo
                Column {
                    Text(
                        text = "Visualizar Clientes / Escopo:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val mainFilters = listOf(
                            AppFilter.ALL to "Todos",
                            AppFilter.USER to "Usuário",
                            AppFilter.SYSTEM to "Sistema",
                            AppFilter.UNINSTALLED to "Desinstalados"
                        )
                        mainFilters.forEach { (filter, label) ->
                            val isSelected = selectedFilter == filter
                            val isUninstalledFilter = filter == AppFilter.UNINSTALLED
                            
                            FilterChip(
                                selected = isSelected,
                                onClick = { onFilterSelected(filter) },
                                label = { 
                                    Text(
                                        text = label, 
                                        fontSize = 11.sp, 
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    ) 
                                },
                                leadingIcon = if (isUninstalledFilter) {
                                    { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(11.dp)) }
                                } else null,
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (isUninstalledFilter) {
                                        Color(0xFF3B82F6).copy(alpha = 0.15f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                    selectedLabelColor = if (isUninstalledFilter) {
                                        Color(0xFF1D4ED8)
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("filter_chip_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                // Seção 2: Níveis de Segurança & Debloat (Organizado em Lugar Único)
                // Esconde-se ao listar os Desinstalados, pois estes já foram limpos
                if (selectedFilter != AppFilter.UNINSTALLED) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Column {
                        Text(
                            text = "Nível de Recomendação (Lugar Único):",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val safetyFilters = listOf(
                                AppFilter.RECOMMENDED to "Recomendados",
                                AppFilter.OPTIONAL to "Opcionais",
                                AppFilter.DANGEROUS to "Perigosos"
                            )
                            safetyFilters.forEach { (filter, label) ->
                                val isSelected = selectedFilter == filter
                                val colorAccent = when (filter) {
                                    AppFilter.RECOMMENDED -> Color(0xFF22C55E)
                                    AppFilter.OPTIONAL -> Color(0xFFF59E0B)
                                    AppFilter.DANGEROUS -> Color(0xFFEF4444)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                val colorText = when (filter) {
                                    AppFilter.RECOMMENDED -> Color(0xFF15803D)
                                    AppFilter.OPTIONAL -> Color(0xFFB45309)
                                    AppFilter.DANGEROUS -> Color(0xFFB91C1C)
                                    else -> MaterialTheme.colorScheme.primary
                                }

                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onFilterSelected(filter) },
                                    label = { 
                                        Text(
                                            text = label, 
                                            fontSize = 11.sp, 
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        ) 
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(colorAccent, CircleShape)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = colorAccent.copy(alpha = 0.15f),
                                        selectedLabelColor = colorText
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("filter_chip_${filter.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Filtro por categoria e segurança",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )

            // Seletor Chip de Ordenação
            Box {
                FilterChip(
                    selected = true,
                    onClick = { showSortMenu = true },
                    label = {
                        val orderText = when (sortOrder) {
                            AppSortOrder.NAME_ASC -> "Nome (A-Z)"
                            AppSortOrder.NAME_DESC -> "Nome (Z-A)"
                            AppSortOrder.SIZE_DESC -> "Tamanho (M-P)"
                            AppSortOrder.SIZE_ASC -> "Tamanho (P-M)"
                            AppSortOrder.INSTALL_DATE_DESC -> "Mais Recente"
                            AppSortOrder.INSTALL_DATE_ASC -> "Mais Antigo"
                        }
                        Text(orderText, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("sorting_chip")
                )

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Nome (A-Z)") },
                        onClick = {
                            showSortMenu = false
                            onSortSelected(AppSortOrder.NAME_ASC)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Nome (Z-A)") },
                        onClick = {
                            showSortMenu = false
                            onSortSelected(AppSortOrder.NAME_DESC)
                        }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Tamanho (Maior para menor)") },
                        onClick = {
                            showSortMenu = false
                            onSortSelected(AppSortOrder.SIZE_DESC)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Tamanho (Menor para maior)") },
                        onClick = {
                            showSortMenu = false
                            onSortSelected(AppSortOrder.SIZE_ASC)
                        }
                    )
                    Divider()
                    DropdownMenuItem(
                        text = { Text("Data: Mais novos primeiro") },
                        onClick = {
                            showSortMenu = false
                            onSortSelected(AppSortOrder.INSTALL_DATE_DESC)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Data: Mais antigos primeiro") },
                        onClick = {
                            showSortMenu = false
                            onSortSelected(AppSortOrder.INSTALL_DATE_ASC)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Item individual de aplicativo na lista
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItemCard(
    appInfo: AppInfo,
    isSelected: Boolean,
    safety: AppSafety,
    onToggleSelect: () -> Unit,
    onBackupApk: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderModifier = if (isSelected) {
        Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .then(borderModifier)
            .clickable(onClick = onToggleSelect)
            .testTag("app_item_${appInfo.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. App Icon
            AppIconView(
                packageName = appInfo.packageName,
                apkPath = appInfo.apkPath,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            )

            Spacer(modifier = Modifier.width(10.dp))

            // 2. Application statistics and info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = appInfo.appName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = appInfo.packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 3. Backup APK button (apenas se for instalado)
            val isUninstalled = appInfo.apkPath == "system" || appInfo.apkPath.contains("APK_Backups")
            if (!isUninstalled) {
                IconButton(
                    onClick = { onBackupApk() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown, // Símbolo para Download/Backup
                        contentDescription = "Backup do APK",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 4. Status indicator checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier
                    .size(24.dp)
                    .testTag("app_checkbox_${appInfo.packageName}")
            )
        }
    }
}

/**
 * Native Android View Wrapper to draw application Icon.
 */
@Composable
fun AppIconView(packageName: String, apkPath: String = "", modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            android.widget.ImageView(ctx).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        },
        update = { view ->
            try {
                val pm = context.packageManager
                val iconDrawable = try {
                    pm.getApplicationIcon(packageName)
                } catch (e: Exception) {
                    if (apkPath.isNotEmpty() && apkPath != "system" && java.io.File(apkPath).exists()) {
                        val packageInfo = pm.getPackageArchiveInfo(apkPath, 0)
                        if (packageInfo != null) {
                            val appInfo = packageInfo.applicationInfo
                            if (appInfo != null) {
                                appInfo.sourceDir = apkPath
                                appInfo.publicSourceDir = apkPath
                                appInfo.loadIcon(pm)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } else if (apkPath == "system") {
                        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                            android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
                        } else {
                            @Suppress("DEPRECATION")
                            android.content.pm.PackageManager.GET_UNINSTALLED_PACKAGES
                        }
                        try {
                            val appInfo = pm.getApplicationInfo(packageName, flags)
                            appInfo.loadIcon(pm)
                        } catch (ex: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }
                
                if (iconDrawable != null) {
                    view.setImageDrawable(iconDrawable)
                } else {
                    view.setImageDrawable(pm.defaultActivityIcon)
                }
            } catch (e: Exception) {
                try {
                    view.setImageDrawable(context.packageManager.defaultActivityIcon)
                } catch (e2: Exception) {
                    view.setImageDrawable(null)
                }
            }
        },
        modifier = modifier
    )
}

/**
 * Estatísticas do Dispositivo Dialog
 */
@Composable
fun DeviceStatisticsDialog(
    stats: DeviceStats,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Estatísticas do Dispositivo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total de Apps Listados:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stats.totalApps}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aplicativos de Usuário:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stats.userApps}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Aplicativos do Sistema:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stats.systemApps}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("🟢 Recomendados (Seguro):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stats.recommendedApps}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("🟡 Opcionais (Bloatware):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stats.optionalApps}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("🔴 Perigosos (SO Crítico):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${stats.dangerousApps}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                }
                Divider()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tamanho Bruto Total (APKs):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stats.formattedTotalSize, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tamanho Médio por App:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stats.formattedAvgSize, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Aviso: O cálculo de tamanho bruto considera as somas de arquivos canônicos base APKs instalados nas pastas de sistema e pacotes split vinculados (apks de recursos). Tamanho de dados locais de sandbox e buffers temporários de cache gerados no dia-a-dia de uso por cada aplicativo não estão incluídos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(8.dp)) {
                Text("Entendido")
            }
        }
    )
}
