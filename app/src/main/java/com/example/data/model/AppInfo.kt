package com.example.data.model

/**
 * Representa os dados consolidados de um aplicativo instalado.
 */
data class AppInfo(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val isSystem: Boolean,
    val sizeBytes: Long,
    val formattedSize: String
)
