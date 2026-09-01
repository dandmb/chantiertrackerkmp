@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.dmb.chantiertracker.data.local

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlock
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val KEYCHAIN_SERVICE = "com.dmb.chantiertracker.auth"
private const val KEY_ACCESS = "access_token"
private const val KEY_REFRESH = "refresh_token"

class IosTokenStorage : TokenStorage {

    override suspend fun get(): AuthTokens? = withContext(Dispatchers.Default) {
        val access = read(KEY_ACCESS) ?: return@withContext null
        val refresh = read(KEY_REFRESH) ?: return@withContext null
        AuthTokens(access, refresh)
    }

    override suspend fun save(tokens: AuthTokens): Unit = withContext(Dispatchers.Default) {
        write(KEY_ACCESS, tokens.accessToken)
        write(KEY_REFRESH, tokens.refreshToken)
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.Default) {
        delete(KEY_ACCESS)
        delete(KEY_REFRESH)
    }

    private fun baseQuery(account: String): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )!!
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        val service = CFBridgingRetain(KEYCHAIN_SERVICE as NSString)
        val acc = CFBridgingRetain(account as NSString)
        CFDictionaryAddValue(query, kSecAttrService, service)
        CFDictionaryAddValue(query, kSecAttrAccount, acc)
        CFBridgingRelease(service)
        CFBridgingRelease(acc)
        return query
    }

    private fun write(account: String, value: String) {
        val query = baseQuery(account)
        SecItemDelete(query)
        val data = CFBridgingRetain((value as NSString).dataUsingEncoding(NSUTF8StringEncoding))
        CFDictionaryAddValue(query, kSecValueData, data)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlock)
        SecItemAdd(query, null)
        CFBridgingRelease(data)
        CFRelease(query)
    }

    private fun read(account: String): String? {
        val query = baseQuery(account)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val value: String? = memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecSuccess) {
                (CFBridgingRelease(result.value) as? NSData)?.decodeToString()
            } else {
                null
            }
        }
        CFRelease(query)
        return value
    }

    private fun delete(account: String) {
        val query = baseQuery(account)
        SecItemDelete(query)
        CFRelease(query)
    }
}

private fun NSData.decodeToString(): String? {
    val length = this.length.toInt()
    if (length == 0) return ""
    val pointer = this.bytes ?: return null
    return pointer.readBytes(length).decodeToString()
}
