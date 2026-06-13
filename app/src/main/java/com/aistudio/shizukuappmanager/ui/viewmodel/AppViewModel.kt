package com.aistudio.shizukuappmanager.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.shizukuappmanager.data.model.AppInfo
import com.aistudio.shizukuappmanager.data.repository.AppRepository
import com.aistudio.shizukuappmanager.shizuku.ShizukuManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppSafety {
    RECOMMENDED,
    OPTIONAL,
    DANGEROUS
}

enum class AppFilter {
    ALL,
    USER,
    SYSTEM,
    RECOMMENDED,
    OPTIONAL,
    DANGEROUS,
    UNINSTALLED
}

enum class AppSortOrder {
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC,
    SIZE_ASC,
    INSTALL_DATE_DESC,
    INSTALL_DATE_ASC,
}

data class DeviceStats(
    val totalApps: Int = 0,
    val userApps: Int = 0,
    val systemApps: Int = 0,
    val recommendedApps: Int = 0,
    val optionalApps: Int = 0,
    val dangerousApps: Int = 0,
    val totalSizeBytes: Long = 0,
    val formattedTotalSize: String = "0 B",
    val avgSizeBytes: Long = 0,
    val formattedAvgSize: String = "0 B"
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application.applicationContext)
    private val TAG = "AppViewModel"

    // Blacklist de pacotes críticos protegidos que não podem ser desinstalados do sistema por segurança
    val CRITICAL_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.android.launcher3",
        "com.android.settings",
        "com.android.providers.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.sec.android.app.launcher",
        "com.samsung.android.honeyboard",
        "com.miui.home",
        "com.xiaomi.miui.home",
        "com.mi.android.globallauncher",
        "com.motorola.launcher3",
        "com.motorola.cn.launcher",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher",
        "com.transsion.hilauncher",
        "dev.rikka.shizuku",
        "moe.shizuku.privileged.api"
    )

    fun getAppSafetyCategory(app: AppInfo): AppSafety {
        val pkg = app.packageName
        
        if (CRITICAL_SYSTEM_PACKAGES.contains(pkg)) {
            return AppSafety.DANGEROUS
        }
        
        if (pkg == "android" ||
            pkg.startsWith("com.android.systemui") ||
            pkg.startsWith("com.android.settings") ||
            pkg.startsWith("com.android.server") ||
            pkg.startsWith("com.google.android.gms") ||
            pkg.startsWith("com.google.android.gsf") ||
            pkg.startsWith("com.android.vending") ||
            pkg.contains(".providers.settings") ||
            pkg.contains("launcher") ||
            pkg.contains("inputmethod") ||
            pkg.contains("keyguard") ||
            pkg.startsWith("dev.rikka.shizuku") ||
            pkg.startsWith("moe.shizuku") ||
            pkg.contains("keyboard") ||
            pkg.contains("lockscreen")
        ) {
            return AppSafety.DANGEROUS
        }
        
        return if (app.isSystem) {
            AppSafety.OPTIONAL
        } else {
            AppSafety.RECOMMENDED
        }
    }

    // Shizuku Status Setup
    private val _isShizukuInstalled = MutableStateFlow(false)
    val isShizukuInstalled = _isShizukuInstalled.asStateFlow()

    private val _isShizukuActive = MutableStateFlow(false)
    val isShizukuActive = _isShizukuActive.asStateFlow()

    private val _isShizukuPermissionGranted = MutableStateFlow(false)
    val isShizukuPermissionGranted = _isShizukuPermissionGranted.asStateFlow()

    // Apps loading states
    private val _rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps = _isLoadingApps.asStateFlow()

    private val _uninstalledApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val uninstalledApps = _uninstalledApps.asStateFlow()

    // Search, Filter and Sort parameters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppFilter.USER) // Começa com Apps do Usuário por segurança
    val selectedFilter = _selectedFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(AppSortOrder.NAME_ASC)
    val sortOrder = _sortOrder.asStateFlow()

    // Selection State
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages = _selectedPackages.asStateFlow()

    // Device overall statistics
    private val _deviceStats = MutableStateFlow(DeviceStats())
    val deviceStats = _deviceStats.asStateFlow()

    // Operational state (desinstalação progressiva)
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _processingProgress = MutableStateFlow(0f)
    val processingProgress = _processingProgress.asStateFlow()

    private val _processingTitle = MutableStateFlow("")
    val processingTitle = _processingTitle.asStateFlow()

    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage = _operationMessage.asStateFlow()

    // Dialogs Triggers
    private val _showConfirmationDialog = MutableStateFlow(false)
    val showConfirmationDialog = _showConfirmationDialog.asStateFlow()

    // Shizuku permission listener
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuManager.SHIZUKU_PERMISSION_REQUEST_CODE) {
            val granted = grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Resultado da permissão Shizuku recebido: $granted")
            _isShizukuPermissionGranted.value = granted
            if (granted) {
                loadAppsList()
            }
        }
    }

    // Combine, filtre e ordene a lista de apps reativamente de acordo com a busca, o filtro e a ordenação selecionados
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _rawApps,
        _uninstalledApps,
        _searchQuery,
        _selectedFilter,
        _sortOrder
    ) { apps, uninstalled, query, filter, sort ->
        val baseList = if (filter == AppFilter.UNINSTALLED) uninstalled else apps
        val filteredList = baseList.filter { app ->
            val matchesSearch = app.appName.contains(query, ignoreCase = true) || 
                                 app.packageName.contains(query, ignoreCase = true)
            
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
                AppFilter.RECOMMENDED -> getAppSafetyCategory(app) == AppSafety.RECOMMENDED
                AppFilter.OPTIONAL -> getAppSafetyCategory(app) == AppSafety.OPTIONAL
                AppFilter.DANGEROUS -> getAppSafetyCategory(app) == AppSafety.DANGEROUS
                AppFilter.UNINSTALLED -> true
            }
            
            matchesSearch && matchesFilter
        }

        when (sort) {
            AppSortOrder.NAME_ASC -> filteredList.sortedBy { it.appName.lowercase(Locale.getDefault()) }
            AppSortOrder.NAME_DESC -> filteredList.sortedByDescending { it.appName.lowercase(Locale.getDefault()) }
            AppSortOrder.SIZE_DESC -> filteredList.sortedByDescending { it.sizeBytes }
            AppSortOrder.SIZE_ASC -> filteredList.sortedBy { it.sizeBytes }
            AppSortOrder.INSTALL_DATE_DESC -> filteredList.sortedByDescending { it.firstInstallTime }
            AppSortOrder.INSTALL_DATE_ASC -> filteredList.sortedBy { it.firstInstallTime }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkShizukuStatus()
        ShizukuManager.addPermissionListener(permissionListener)
        
        // Se já tiver permissão ativa, carrega de início
        if (ShizukuManager.isActive() && ShizukuManager.isPermissionGranted()) {
            loadAppsList()
        } else {
            // Recorrer ao carregamento padrão do Package Manager independente da permissão Shizuku (permite listagem rápida)
            loadAppsList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        ShizukuManager.removePermissionListener(permissionListener)
    }

    fun checkShizukuStatus() {
        val context = getApplication<Application>().applicationContext
        val installed = ShizukuManager.isInstalled(context)
        val active = installed && ShizukuManager.isActive()
        val granted = active && ShizukuManager.isPermissionGranted()

        _isShizukuInstalled.value = installed
        _isShizukuActive.value = active
        _isShizukuPermissionGranted.value = granted
        Log.d(TAG, "Shizuku Status -> Instalado: $installed | Ativo: $active | Permissão: $granted")
    }

    fun requestShizukuPermission() {
        viewModelScope.launch {
            if (!ShizukuManager.isActive()) {
                _operationMessage.value = "Shizuku inativo no dispositivo. Inicie o app Shizuku antes."
                return@launch
            }
            ShizukuManager.requestPermission()
            checkShizukuStatus()
        }
    }

    fun loadAppsList() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                val list = repository.getInstalledApps()
                _rawApps.value = list
                
                // Busca de aplicativos desinstalados que estão nos backups ou são de sistema desinstalados
                val activePackagesSet = list.map { it.packageName }.toSet()
                val uninstalledList = repository.getUninstalledApps(activePackagesSet)
                _uninstalledApps.value = uninstalledList

                // Limpa seleções de pacotes que não existem em nenhuma das listas
                val allValidPackages = activePackagesSet + uninstalledList.map { it.packageName }.toSet()
                _selectedPackages.value = _selectedPackages.value.filter { allValidPackages.contains(it) }.toSet()

                // Atualizar estatísticas de forma consolidada (somente de instalados)
                val totalApps = list.size
                val systemApps = list.count { it.isSystem }
                val userApps = totalApps - systemApps
                val recommendedApps = list.count { getAppSafetyCategory(it) == AppSafety.RECOMMENDED }
                val optionalApps = list.count { getAppSafetyCategory(it) == AppSafety.OPTIONAL }
                val dangerousApps = list.count { getAppSafetyCategory(it) == AppSafety.DANGEROUS }
                val totalSize = list.sumOf { it.sizeBytes }
                val avgSize = if (totalApps > 0) totalSize / totalApps else 0L

                _deviceStats.value = DeviceStats(
                    totalApps = totalApps,
                    userApps = userApps,
                    systemApps = systemApps,
                    recommendedApps = recommendedApps,
                    optionalApps = optionalApps,
                    dangerousApps = dangerousApps,
                    totalSizeBytes = totalSize,
                    formattedTotalSize = formatSize(totalSize),
                    avgSizeBytes = avgSize,
                    formattedAvgSize = formatSize(avgSize)
                )

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar lista de apps no ViewModel", e)
                _operationMessage.value = "Erro ao ler aplicativos: ${e.localizedMessage}"
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun toggleAppSelection(packageName: String) {
        val current = _selectedPackages.value.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _selectedPackages.value = current
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }

    fun selectAllInCurrentFilter() {
        val currentFiltered = filteredApps.value
        val currentSelected = _selectedPackages.value.toMutableSet()
        
        val allFilteredPackages = currentFiltered.map { it.packageName }
        
        // Se todos os filtrados já estiverem selecionados, limpe as seleções deles
        if (currentSelected.containsAll(allFilteredPackages)) {
            currentSelected.removeAll(allFilteredPackages.toSet())
        } else {
            currentSelected.addAll(allFilteredPackages)
        }
        
        _selectedPackages.value = currentSelected
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: AppFilter) {
        _selectedFilter.value = filter
    }

    fun setSortOrder(order: AppSortOrder) {
        _sortOrder.value = order
    }

    fun triggerUninstallConfirmation(show: Boolean) {
        if (show && _selectedPackages.value.isEmpty()) {
            _operationMessage.value = "Nenhum aplicativo selecionado para desinstalação."
            return
        }
        _showConfirmationDialog.value = show
    }

    fun dismissOperationMessage() {
        _operationMessage.value = null
    }

    /**
     * Executa a desinstalação segura em sequência dos aplicativos selecionados no Shizuku.
     * Incorpora detecção antivazamento e pula componentes essenciais do sistema operacional.
     */
    fun startUninstallSelectedApps() {
        val packagesToUninstall = _selectedPackages.value.toList()
        if (packagesToUninstall.isEmpty()) return

        _showConfirmationDialog.value = false
        
        // Filtra contra remoção acidental de componentes críticos do SO (Bloqueio de Segurança)
        val protectedAppsToSkip = packagesToUninstall.filter { CRITICAL_SYSTEM_PACKAGES.contains(it) }
        val finalPackagesToUninstall = packagesToUninstall.filter { !CRITICAL_SYSTEM_PACKAGES.contains(it) }

        if (finalPackagesToUninstall.isEmpty() && protectedAppsToSkip.isNotEmpty()) {
            val names = protectedAppsToSkip.map { pkg ->
                _rawApps.value.find { it.packageName == pkg }?.appName ?: pkg
            }.joinToString(", ")
            _operationMessage.value = "Bloqueio de Segurança: A operação foi cancelada pois os pacotes selecionados ($names) são críticos para estabilidade do dispositivo e protegidos pelo sistema de segurança."
            return
        }

        _isProcessing.value = true
        _processingProgress.value = 0f
        
        val totalCount = finalPackagesToUninstall.size
        val resultsSummary = StringBuilder()
        var successes = 0
        var failures = 0
        var skippedCount = protectedAppsToSkip.size

        if (protectedAppsToSkip.isNotEmpty()) {
            resultsSummary.append("Componentes protegidos e preservados:\n")
            protectedAppsToSkip.forEach { pkg ->
                val name = _rawApps.value.find { it.packageName == pkg }?.appName ?: pkg
                resultsSummary.append("🛡️ $name (Ignorado por Segurança)\n")
            }
            resultsSummary.append("\nDesinstalações processadas:\n")
        }

        viewModelScope.launch {
            finalPackagesToUninstall.forEachIndexed { index, packageName ->
                val appInfo = _rawApps.value.find { it.packageName == packageName }
                val displayName = appInfo?.appName ?: packageName
                
                _processingTitle.value = "Desinstalando ($index/$totalCount): $displayName"
                _processingProgress.value = index.toFloat() / totalCount.toFloat()

                // Desinstalar via Shizuku
                val isSystemApp = appInfo?.isSystem ?: false
                val result = ShizukuManager.uninstallApp(packageName, deleteForUserZeroOnly = isSystemApp)
                
                if (result.first) {
                    successes++
                    resultsSummary.append("✓ $displayName: Sucesso\n")
                } else {
                    failures++
                    resultsSummary.append("✗ $displayName: ${result.second}\n")
                }
            }

            _processingProgress.value = 1f
            _isProcessing.value = false
            _selectedPackages.value = emptySet()
            
            // Recarregar os aplicativos remanescentes
            loadAppsList()

            // Concluir mensagem de relatório
            val skippedNotice = if (skippedCount > 0) " | Preservados por segurança: $skippedCount" else ""
            val finalMessage = """
                Operação concluída!
                Sucessos: $successes | Falhas: $failures$skippedNotice
                
                Detalhes:
                $resultsSummary
            """.trimIndent()
            
            _operationMessage.value = finalMessage
        }
    }

    /**
     * Executa a restauração em sequência de aplicativos que foram desinstalados ou arquivados.
     */
    fun startRestoreSelectedApps() {
        val packagesToRestore = _selectedPackages.value.toList()
        if (packagesToRestore.isEmpty()) return

        _isProcessing.value = true
        _processingProgress.value = 0f
        
        val totalCount = packagesToRestore.size
        val resultsSummary = StringBuilder()
        var successes = 0
        var failures = 0

        viewModelScope.launch {
            packagesToRestore.forEachIndexed { index, packageName ->
                // Busca se há no backup de uninstalled
                val appInfo = _uninstalledApps.value.find { it.packageName == packageName }
                val displayName = appInfo?.appName ?: packageName
                val apkPath = appInfo?.apkPath ?: "system"
                
                _processingTitle.value = "Restaurando ($index/$totalCount): $displayName"
                _processingProgress.value = index.toFloat() / totalCount.toFloat()

                // Restaurar usando o Shizuku
                val result = ShizukuManager.restoreApp(packageName, apkPath)
                
                if (result.first) {
                    successes++
                    resultsSummary.append("✓ $displayName: Sucesso \n")
                } else {
                    failures++
                    resultsSummary.append("✗ $displayName: ${result.second}\n")
                }
            }

            _processingProgress.value = 1f
            _isProcessing.value = false
            _selectedPackages.value = emptySet()
            
            // Recarregar os aplicativos e atualizar backups
            loadAppsList()

            // Concluir mensagem de relatório
            val finalMessage = """
                Operação de restauração concluída!
                Sucessos: $successes | Falhas: $failures
                
                Detalhes:
                $resultsSummary
            """.trimIndent()
            
            _operationMessage.value = finalMessage
        }
    }

    /**
     * Realiza o backup físico do APK de um aplicativo para a pasta interna de backups do armazenamento externo do app.
     * Funciona sem requisitar permissões complexas e protege os dados do usuário.
     */
    fun backupSelectedApk(app: AppInfo) {
        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val srcFile = File(app.apkPath)
            if (!srcFile.exists()) {
                _operationMessage.value = "Erro: Arquivo APK original não pôde ser encontrado em '${app.apkPath}'."
                return@launch
            }
            
            try {
                val destDir = File(context.getExternalFilesDir(null), "APK_Backups")
                if (!destDir.exists()) {
                    destDir.mkdirs()
                }
                
                val sanitizedName = app.appName.replace("[^a-zA-Z0-9]".toRegex(), "_")
                val destFile = File(destDir, "${sanitizedName}_v${app.versionName}.apk")
                
                _isLoadingApps.value = true // dar feedback visual breve
                
                srcFile.inputStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                _operationMessage.value = """
                    Backup concluído com sucesso!
                    
                    App: ${app.appName}
                    Caminho: ${destFile.absolutePath}
                    Tamanho: ${app.formattedSize}
                    
                    O arquivo APK foi preservado com segurança e pode ser compartilhado ou reinstalado.
                """.trimIndent()
            } catch (e: Exception) {
                Log.e(TAG, "Falha de entrada/saída ao copiar APK", e)
                _operationMessage.value = "Erro ao fazer backup do APK: ${e.localizedMessage}\n\nNota: Em aparelhos com SELinux severo ou Android 14+, certas partições de apps protegidos podem ter privilégios de leitura negados para apps terceiros."
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    /**
     * Exporta os aplicativos filtrados no momento para os formatos TXT ou CSV.
     * Também salva o conteúdo textual diretamente para a área de transferência facilitando compartilhamento imediato.
     */
    fun exportFilteredAppsList(format: String) {
        val list = filteredApps.value
        if (list.isEmpty()) {
            _operationMessage.value = "Aviso: A lista atual de aplicativos filtrados está vazia. Nada para exportar."
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            val textBuilder = StringBuilder()
            
            if (format.lowercase(Locale.getDefault()) == "csv") {
                // Header CSV
                textBuilder.append("Nome do App,Nome do Pacote,Versao,Tipo,Tamanho,SDK Alvo,Data Instalacao\n")
                list.forEach { app ->
                    val type = if (app.isSystem) "Sistema" else "Usuario"
                    val escapedName = app.appName.replace("\"", "\"\"")
                    textBuilder.append("\"$escapedName\",${app.packageName},${app.versionName},$type,${app.formattedSize},${app.targetSdk},${app.formattedInstallDate}\n")
                }
            } else {
                // Formato TXT
                textBuilder.append("==================================================\n")
                textBuilder.append("    RELATÓRIO DE APLICATIVOS INSTALADOS (${list.size} APPS)\n")
                textBuilder.append("    Gerado em: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                textBuilder.append("==================================================\n\n")
                
                list.forEachIndexed { index, app ->
                    val type = if (app.isSystem) "SISTEMA" else "USUÁRIO"
                    textBuilder.append("${index + 1}. ${app.appName}\n")
                    textBuilder.append("   • Pacote: ${app.packageName}\n")
                    textBuilder.append("   • Versão: ${app.versionName}\n")
                    textBuilder.append("   • Tipo: $type\n")
                    textBuilder.append("   • Tamanho: ${app.formattedSize}\n")
                    textBuilder.append("   • SDK Alvo: API ${app.targetSdk}\n")
                    textBuilder.append("   • Data de Instalação: ${app.formattedInstallDate}\n")
                    textBuilder.append("--------------------------------------------------\n")
                }
            }

            val finalStr = textBuilder.toString()
            
            try {
                // Copiar para a área de transferência (Clipboard) por conveniência
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Lista de Apps Shizuku Manager", finalStr)
                clipboard.setPrimaryClip(clip)

                // Salvar backup local físico em cache para reuso se desejado
                val destFile = File(context.cacheDir, "lista_apps_exportada.${format.lowercase(Locale.getDefault())}")
                destFile.writeText(finalStr)

                _operationMessage.value = """
                    Lista exportada com sucesso em formato ${format.uppercase(Locale.getDefault())}!
                    
                    1. Copiado para a área de transferência!
                    Pronto para colar em qualquer aplicativo (Notas, E-mail, Excel, etc).
                    
                    2. Salvo localmente em cache:
                    ${destFile.absolutePath}
                """.trimIndent()

            } catch (e: Exception) {
                Log.e(TAG, "Erro ao gravar exportação em texto", e)
                _operationMessage.value = "Falha ao exportar lista: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Gera e compartilha um relatório legível com a lista dos aplicativos desinstalados no aparelho.
     * Oferece compartilhamento dinâmico via Android Share Sheet para e-mail, WhatsApp, Notas, etc.
     */
    fun shareUninstalledAppsList(context: Context) {
        val list = _uninstalledApps.value
        if (list.isEmpty()) {
            _operationMessage.value = "Aviso: Não há aplicativos desinstalados para compartilhar."
            return
        }

        val textBuilder = StringBuilder()
        textBuilder.append("==================================================\n")
        textBuilder.append("    MINHA LISTA DE APLICATIVOS DESINSTALADOS (${list.size} APPS)\n")
        textBuilder.append("    Gerado pelo Shizuku App Manager\n")
        textBuilder.append("    Data: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        textBuilder.append("==================================================\n\n")

        list.forEachIndexed { index, app ->
            val type = if (app.isSystem) "Sistema / Bloatware desinstalado" else "Usuário / Backup físico local"
            textBuilder.append("${index + 1}. ${app.appName}\n")
            textBuilder.append("   • Pacote: ${app.packageName}\n")
            textBuilder.append("   • Versão: ${app.versionName}\n")
            textBuilder.append("   • Tipo: $type\n")
            if (app.sizeBytes > 0) {
                textBuilder.append("   • Tamanho: ${app.formattedSize}\n")
            }
            textBuilder.append("   • SDK Alvo: API ${app.targetSdk}\n")
            textBuilder.append("--------------------------------------------------\n")
        }

        val reportText = textBuilder.toString()

        try {
            // Copiar também para clipboard para facilidade do usuário
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Lista de Desinstalados Shizuku", reportText)
            clipboard.setPrimaryClip(clip)

            // Salvar em arquivo físico temporário em cache de compartilhamento caso outro app precise receber como documento
            val destFile = File(context.cacheDir, "lista_desinstalados.txt")
            destFile.writeText(reportText)

            // Abrir e iniciar o Intent do Android Share Chooser
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Minha lista de apps desinstalados")
                putExtra(android.content.Intent.EXTRA_TEXT, reportText)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooserIntent = android.content.Intent.createChooser(shareIntent, "Compartilhar Lista de Desinstalados")
            chooserIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)

            _operationMessage.value = "Lista de Desinstalados copiada e compartilhada!\n\nSeu relatório de ${list.size} apps desinstalados foi enviado para a área de transferência e o painel de compartilhamento foi aberto com sucesso."
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao compartilhar a lista de desinstalados", e)
            _operationMessage.value = "Erro ao exportar/compartilhar lista: ${e.localizedMessage}"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups < units.size) digitGroups else units.size - 1
        return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }
}
