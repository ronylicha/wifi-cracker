package com.wificracker.core.root

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RootChecker @Inject constructor(
    private val shellExecutor: ShellExecutor,
) {

    fun isRooted(): Boolean {
        val result = shellExecutor.execute("su -c id")
        return result.isSuccess && result.stdout.contains("uid=0")
    }

    fun detectRootType(): RootType {
        if (!isRooted()) return RootType.NONE
        if (shellExecutor.execute("su -c which magisk").isSuccess) return RootType.MAGISK
        if (shellExecutor.execute("su -c which ksud").isSuccess) return RootType.KERNELSU
        if (shellExecutor.execute("su -c which su").stdout.contains("supersu", ignoreCase = true)) {
            return RootType.SUPERSU
        }
        return RootType.UNKNOWN
    }
}
