package com.example.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_PERMISSION_REQUEST_CODE = 4220

    /**
     * Verifica se o Shizuku está instalado no dispositivo.
     */
    fun isInstalled(context: Context): Boolean {
        val packages = listOf("rikka.app.shizuku", "moe.shizuku.privileged.api")
        val pm = context.packageManager
        for (pkg in packages) {
            try {
                // flags 0 para verificação simples
                pm.getPackageInfo(pkg, 0)
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Tenta o próximo
            }
        }
        return false
    }

    /**
     * Verifica se o serviço Shizuku está ativo e o Binder está respondendo.
     */
    fun isActive(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao pingar Shizuku Binder", e)
            false
        }
    }

    /**
     * Verifica se o aplicativo já possui permissão de uso do Shizuku.
     */
    fun isPermissionGranted(): Boolean {
        return try {
            if (Shizuku.isPreV11()) {
                // Versões pré-v11 aceitam de forma implícita se estiver rodando
                true
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao verificar permissão do Shizuku", e)
            false
        }
    }

    /**
     * Solicita permissão de acesso ao Shizuku.
     */
    fun requestPermission(requestCode: Int = SHIZUKU_PERMISSION_REQUEST_CODE) {
        try {
            if (!Shizuku.isPreV11()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao solicitar permissão no Shizuku", e)
        }
    }

    /**
     * Adiciona um ouvinte para receber o resultado da solicitação de permissão.
     */
    fun addPermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.addRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao registrar listener Shizuku", e)
        }
    }

    /**
     * Remove o ouvinte cadastrado.
     */
    fun removePermissionListener(listener: Shizuku.OnRequestPermissionResultListener) {
        try {
            Shizuku.removeRequestPermissionResultListener(listener)
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao descadastrar listener Shizuku", e)
        }
    }

    /**
     * Executa a desinstalação de um aplicativo via processo privilegiado do Shizuku.
     * Retorna um par contendo (Sucesso (Boolean), Mensagem de Saída (String)).
     */
    suspend fun uninstallApp(packageName: String, deleteForUserZeroOnly: Boolean = true): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (!isActive()) {
            return@withContext Pair(false, "Serviço Shizuku inativo.")
        }
        if (!isPermissionGranted()) {
            return@withContext Pair(false, "Permissão Shizuku negada.")
        }

        try {
            // Comando para desinstalação via ADB privilegiado do PM (Package Manager)
            // pm uninstall -k --user 0 <package_name> (ideal para desinstalar apps instalados de fábrica/sistema e do usuário padrão)
            val command = if (deleteForUserZeroOnly) {
                arrayOf("pm", "uninstall", "--user", "0", packageName)
            } else {
                arrayOf("pm", "uninstall", packageName)
            }

            Log.d(TAG, "Iniciando desinstalação via Shizuku: $packageName")
            val method = Shizuku::class.java.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            val process = method.invoke(null, command, null, null) as java.lang.Process
            
            // Lê canais de saída em paralelo para não travar o buffer do processo
            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val stdoutJob = launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stdoutBuilder.append(line).append("\n")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Falha na leitura stdout", e)
                }
            }

            val stderrJob = launch {
                try {
                    val reader = BufferedReader(InputStreamReader(process.errorStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        stderrBuilder.append(line).append("\n")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Falha na leitura stderr", e)
                }
            }

            val exitCode = process.waitFor()
            stdoutJob.join()
            stderrJob.join()

            val stdoutStr = stdoutBuilder.toString().trim()
            val stderrStr = stderrBuilder.toString().trim()

            Log.d(TAG, "Exit code: $exitCode | Out: $stdoutStr | Err: $stderrStr")

            // Se stdout contiver "Success" ou exitCode for 0, consideramos bem sucedido.
            val isSuccess = exitCode == 0 || stdoutStr.contains("Success", ignoreCase = true)
            val resultMsg = if (isSuccess) {
                "Sucesso: $stdoutStr"
            } else {
                "Falha ($exitCode): ${stderrStr.ifEmpty { stdoutStr.ifEmpty { "Erro desconhecido" } }}"
            }

            Pair(isSuccess, resultMsg)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao desinstalar o app $packageName", e)
            Pair(false, e.localizedMessage ?: "Erro de E/S ou de execução Binder")
        }
    }
}
