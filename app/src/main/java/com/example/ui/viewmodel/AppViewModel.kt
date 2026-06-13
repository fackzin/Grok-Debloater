package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AppInfo
import com.example.data.repository.AppRepository
import com.example.shizuku.ShizukuManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppFilter {
    ALL,
    USER,
    SYSTEM
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application.applicationContext)
    private val TAG = "AppViewModel"

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

    // Search and Filter parameters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppFilter.USER) // Começa com Apps do Usuário por segurança
    val selectedFilter = _selectedFilter.asStateFlow()

    // Selection State
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages = _selectedPackages.asStateFlow()

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

    // Combine e filtre a lista de apps reativamente de acordo com a busca e o filtro selecionado
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _rawApps,
        _searchQuery,
        _selectedFilter
    ) { apps, query, filter ->
        apps.filter { app ->
            val matchesSearch = app.appName.contains(query, ignoreCase = true) || 
                                app.packageName.contains(query, ignoreCase = true)
            
            val matchesFilter = when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
            }
            
            matchesSearch && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkShizukuStatus()
        ShizukuManager.addPermissionListener(permissionListener)
        
        // Se já tiver permissão ativa, carrega de início
        if (ShizukuManager.isActive() && ShizukuManager.isPermissionGranted()) {
            loadAppsList()
        } else {
            // Recorre a carregamento padrão do Package Manager independente da permissão Shizuku (permite listagem rápida)
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
            // Atualiza status local
            checkShizukuStatus()
        }
    }

    fun loadAppsList() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            try {
                val list = repository.getInstalledApps()
                _rawApps.value = list
                // Limpa seleções de pacotes que não existem mais
                val packagesSet = list.map { it.packageName }.toSet()
                _selectedPackages.value = _selectedPackages.value.filter { packagesSet.contains(it) }.toSet()
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
     * Executa a desinstalação em sequência dos aplicativos selecionados no Shizuku.
     */
    fun startUninstallSelectedApps() {
        val packagesToUninstall = _selectedPackages.value.toList()
        if (packagesToUninstall.isEmpty()) return

        _showConfirmationDialog.value = false
        _isProcessing.value = true
        _processingProgress.value = 0f
        
        val totalCount = packagesToUninstall.size
        val resultsSummary = StringBuilder()
        var successes = 0
        var failures = 0

        viewModelScope.launch {
            packagesToUninstall.forEachIndexed { index, packageName ->
                val appInfo = _rawApps.value.find { it.packageName == packageName }
                val displayName = appInfo?.appName ?: packageName
                
                _processingTitle.value = "Desinstalando ($index/$totalCount): $displayName"
                _processingProgress.value = index.toFloat() / totalCount.toFloat()

                // Desinstalar via Shizuku
                // Se for app de sistema, removemos para o usuário 0 por padrão
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
            val finalMessage = """
                Operação concluída!
                Sucessos: $successes | Falhas: $failures
                
                Detalhes:
                $resultsSummary
            """.trimIndent()
            
            _operationMessage.value = finalMessage
        }
    }
}
