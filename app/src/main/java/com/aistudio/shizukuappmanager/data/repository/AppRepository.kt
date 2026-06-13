package com.aistudio.shizukuappmanager.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.aistudio.shizukuappmanager.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
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

            val firstInstallTime = packageInfo.firstInstallTime
            val formattedInstallDate = try {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                sdf.format(Date(firstInstallTime))
            } catch (e: Exception) {
                "-"
            }
            val targetSdk = appInfo.targetSdkVersion
            val apkPath = appInfo.sourceDir ?: ""

            AppInfo(
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                isSystem = isSystem,
                sizeBytes = sizeBytes,
                formattedSize = formatSize(sizeBytes),
                firstInstallTime = firstInstallTime,
                formattedInstallDate = formattedInstallDate,
                targetSdk = targetSdk,
                apkPath = apkPath
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

    /**
     * Recupera aplicativos já desinstalados (pelo backup local ou pacotes de sistema desinstalados sob o usuário 0).
     */
    suspend fun getUninstalledApps(installedPackages: Set<String>): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val uninstalledList = mutableListOf<AppInfo>()
        
        // 1. Escanear APK_Backups local
        val destDir = File(context.getExternalFilesDir(null), "APK_Backups")
        if (destDir.exists() && destDir.isDirectory) {
            val apkFiles = destDir.listFiles { file -> file.extension.lowercase(Locale.getDefault()) == "apk" }
            if (apkFiles != null) {
                for (file in apkFiles) {
                    try {
                        val archiveInfo = pm.getPackageArchiveInfo(file.absolutePath, 0)
                        if (archiveInfo != null) {
                            val appInfoObj = archiveInfo.applicationInfo
                            if (appInfoObj != null) {
                                val packageName = archiveInfo.packageName
                                if (!installedPackages.contains(packageName)) {
                                    appInfoObj.sourceDir = file.absolutePath
                                    appInfoObj.publicSourceDir = file.absolutePath
                                    val appName = appInfoObj.loadLabel(pm).toString().ifEmpty { packageName }
                                    val versionName = archiveInfo.versionName ?: "1.0"
                                    val sizeBytes = file.length()
                                    
                                    uninstalledList.add(
                                        AppInfo(
                                            appName = appName,
                                            packageName = packageName,
                                            versionName = versionName,
                                            isSystem = false,
                                            sizeBytes = sizeBytes,
                                            formattedSize = formatSize(sizeBytes),
                                            firstInstallTime = file.lastModified(),
                                            formattedInstallDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(file.lastModified())),
                                            targetSdk = appInfoObj.targetSdkVersion,
                                            apkPath = file.absolutePath
                                        )
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("AppRepository", "Erro ao ler arquivo APK de backup: ${file.name}", e)
                    }
                }
            }
        }
        
        // 2. Escanear pacotes de sistema desinstalados (p. ex. debloat sob usuário 0)
        try {
            @Suppress("DEPRECATION")
            val getUninstalledFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            } else {
                PackageManager.GET_UNINSTALLED_PACKAGES
            }
            
            @Suppress("DEPRECATION")
            val allPackages = pm.getInstalledPackages(getUninstalledFlags)
            
            for (pkgInfo in allPackages) {
                val packageName = pkgInfo.packageName
                if (!installedPackages.contains(packageName)) {
                    val appInfo = pkgInfo.applicationInfo ?: continue
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    
                    if (isSystem) {
                        val appName = try {
                            appInfo.loadLabel(pm).toString()
                        } catch (e: Exception) {
                            packageName
                        }
                        
                        uninstalledList.add(
                            AppInfo(
                                appName = appName.ifEmpty { packageName },
                                packageName = packageName,
                                versionName = pkgInfo.versionName ?: "1.0",
                                isSystem = true,
                                sizeBytes = 0L,
                                formattedSize = "Sistema",
                                firstInstallTime = pkgInfo.firstInstallTime,
                                formattedInstallDate = "-",
                                targetSdk = appInfo.targetSdkVersion,
                                apkPath = "system"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AppRepository", "Erro ao obter pacotes desinstalados do sistema", e)
        }
        
        uninstalledList.distinctBy { it.packageName }.sortedBy { it.appName.lowercase(Locale.getDefault()) }
    }

    // Helper interno para tratamento de compatibilidade com Android 13+ (Tiramisu)
    private object BuildCompat {
        fun isAtLeastT(): Boolean {
            return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        }
    }
}
