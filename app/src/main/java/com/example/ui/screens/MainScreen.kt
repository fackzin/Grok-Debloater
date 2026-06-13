package com.example.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.model.AppInfo
import com.example.ui.viewmodel.AppFilter
import com.example.ui.viewmodel.AppViewModel
import kotlinx.coroutines.launch

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
    val selectedPackages by viewModel.selectedPackages.collectAsState()

    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val processingTitle by viewModel.processingTitle.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    val showConfirmationDialog by viewModel.showConfirmationDialog.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "App Manager",
                            fontWeight = FontWeight.Medium,
                            fontSize = 20.sp
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
                        // Desenhando um ícone de refresh customizado caso não esteja no pack principal
                        Icon(
                            imageVector = Icons.Default.Settings, // Usamos Settings ou um refresh customizado
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
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selecionar Todos"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            AnimatedVisibility(
                visible = selectedPackages.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    tonalElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface,
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Isso desinstalará os apps selecionados permanente do dispositivo principal.",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { viewModel.triggerUninstallConfirmation(true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp),
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

            // 2. Search and filter deck
            SearchAndFilterHeader(
                searchQuery = searchQuery,
                onSearchChanged = { viewModel.setSearchQuery(it) },
                selectedFilter = selectedFilter,
                onFilterSelected = { viewModel.setFilter(it) }
            )

            // 3. Application List
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
                            text = "Lendo aplicativos instalados...",
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
                    contentPadding = PaddingValues(bottom = 80.dp) // espaço para o bottom bar quando aparece
                ) {
                    items(items = appsList, key = { it.packageName }) { appInfo ->
                        val isSelected = selectedPackages.contains(appInfo.packageName)
                        AppItemCard(
                            appInfo = appInfo,
                            isSelected = isSelected,
                            onToggleSelect = { viewModel.toggleAppSelection(appInfo.packageName) }
                        )
                    }
                }
            }
        }
    }

    // Confirmation uninstallation dialog
    if (showConfirmationDialog) {
        val selectedCount = selectedPackages.size
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
                    text = "Atenção: Desinstalar aplicativos?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Você tem certeza de que deseja desinstalar os $selectedCount aplicativos selecionados via Shizuku?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aviso para Apps do Sistema: Eles serão desinstalados para o perfil padrão (usuário 0). Se desinstalar um app necessário para o sistema operacional, isso pode gerar instabilidade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        LazyColumn(modifier = Modifier.padding(8.dp)) {
                            val itemsToShow = appsList.filter { selectedPackages.contains(it.packageName) }
                            items(itemsToShow) { item ->
                                Text(
                                    text = "• ${item.appName} (${item.packageName})",
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                    Text("Sim, Desinstalar", fontWeight = FontWeight.Bold)
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

    // Loading overlay when processing batch uninstalls
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
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Por favor, aguarde. Os pacotes estão sendo desinstalados de forma segura.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { processingProgress },
                        modifier = Modifier.fillMaxWidth().clip(CircleShape)
                    )
                }
            }
        }
    }

    // Results / Error summary popup when operation completes
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
                    text = "Ação Executada",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    LazyColumn {
                        item {
                            Text(
                                text = operationMessage ?: "",
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissOperationMessage() },
                    modifier = Modifier.testTag("dismiss_result_button")
                ) {
                    Text("OK")
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
    val isReady = installed && active && permissionGranted
    val dotColor = when {
        !installed -> MaterialTheme.colorScheme.error
        !active -> Color(0xFFF59E0B) // Amber
        !permissionGranted -> Color(0xFF3B82F6) // Blue
        else -> Color(0xFF22C55E) // Green (Ready)
    }

    val titleText = when {
        !installed -> "Shizuku não Detectado"
        !active -> "Shizuku Inativo"
        !permissionGranted -> "Shizuku Não Autorizado"
        else -> "Shizuku Ativo"
    }

    val subtitleText = when {
        !installed -> "Instale o app oficial Shizuku"
        !active -> "Serviço em segundo plano inativo"
        !permissionGranted -> "Requer aprovação de permissão"
        else -> "Versão 13.5.4 • Autorizado"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
                // Glowing Status Dot
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
                            text = "AUTORIZAR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }
                } else if (!isReady) {
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
                } else {
                    Text(
                        text = "PRONTO",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Filtro e Pesquisa
 */
@Composable
fun SearchAndFilterHeader(
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    selectedFilter: AppFilter,
    onFilterSelected: (AppFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Pesquisar por nome ou pacote...") },
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

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppFilter.values().forEach { filter ->
                val isSelected = selectedFilter == filter
                val label = when (filter) {
                    AppFilter.ALL -> "Todos"
                    AppFilter.USER -> "Usuário"
                    AppFilter.SYSTEM -> "Sistema"
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(label, fontSize = 12.sp) },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("filter_chip_${filter.name.lowercase()}")
                )
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
    onToggleSelect: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderModifier = if (isSelected) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
    } else {
        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
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
            // 1. Android Native Icon View
            AppIconView(
                packageName = appInfo.packageName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color.LightGray, RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 2. App stats details
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
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (appInfo.isSystem) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "SISTEMA",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = appInfo.packageName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "v${appInfo.versionName}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = appInfo.formattedSize,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3. Status indicator checkbox
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.testTag("app_checkbox_${appInfo.packageName}")
            )
        }
    }
}

/**
 * Native Android View Wrapper to draw application Icon.
 */
@Composable
fun AppIconView(packageName: String, modifier: Modifier = Modifier) {
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
                val iconDrawable = context.packageManager.getApplicationIcon(packageName)
                view.setImageDrawable(iconDrawable)
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
