package com.example.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class AppRepository(private val context: Context) {

    /**
     * Recupera todos os aplicativos instalados no dispositivo de forma assíncrona.
     */
    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        
        val packages: List<PackageInfo> = try {
            if (BuildCompat.isAtLeastT()) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(0)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Erro ao obter pacotes instalados", e)
            emptyList()
        }

        packages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            
            // Filtra o próprio app de gerenciar a si mesmo se necessário, mas pode deixar listar
            val packageName = packageInfo.packageName
            val appName = appInfo.loadLabel(pm).toString()
            val versionName = packageInfo.versionName ?: "1.0"
            
            // Verifica se é aplicativo de sistema
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            
            // Calcula o tamanho da base APK e dos pacotes de recursos (split APKs)
            var sizeBytes = 0L
            val sourceFile = File(appInfo.sourceDir)
            if (sourceFile.exists()) {
                sizeBytes += sourceFile.length()
            }
            
            val splitDirs = appInfo.splitSourceDirs
            if (splitDirs != null) {
                for (dir in splitDirs) {
                    val file = File(dir)
                    if (file.exists()) {
                        sizeBytes += file.length()
                    }
                }
            }

            AppInfo(
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                isSystem = isSystem,
                sizeBytes = sizeBytes,
                formattedSize = formatSize(sizeBytes)
            )
        }.sortedBy { it.appName.lowercase(Locale.getDefault()) }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val index = if (digitGroups < units.size) digitGroups else units.size - 1
        return String.format(Locale.getDefault(), "%.2f %s", bytes / Math.pow(1024.0, index.toDouble()), units[index])
    }

    // Helper interno para tratamento de compatibilidade com Android 13+ (Tiramisu)
    private object BuildCompat {
        fun isAtLeastT(): Boolean {
            return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        }
    }
}
