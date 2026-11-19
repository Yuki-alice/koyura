package app.koyura.data.session

import app.koyura.source.runtime.SourceSecretStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class)
internal class IosSourceSecretStore : SourceSecretStore {
    override fun read(sourceKey: String): String? = query(sourceKey) { query ->
        CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound) return@memScoped null
            check(status == errSecSuccess) { "Could not read source session from Keychain ($status)" }
            val data = checkNotNull(result.value) as CFDataRef
            try {
                val size = CFDataGetLength(data).toInt()
                CFDataGetBytePtr(data)!!.readBytes(size).decodeToString()
            } finally {
                CFRelease(data)
            }
        }
    }

    override fun write(sourceKey: String, value: String?) = query(sourceKey) { query ->
        if (value == null) {
            val status = SecItemDelete(query)
            check(status == errSecSuccess || status == errSecItemNotFound) { "Could not remove source session from Keychain ($status)" }
            return@query
        }
        val bytes = value.encodeToByteArray()
        val data = bytes.usePinned { pinned ->
            checkNotNull(CFDataCreate(null, pinned.addressOf(0).reinterpret(), bytes.size.toLong()))
        }
        val update = checkNotNull(CFDictionaryCreateMutable(null, 0, null, null))
        try {
            CFDictionarySetValue(update, kSecValueData, data)
            var status = SecItemUpdate(query, update)
            if (status == errSecItemNotFound) {
                CFDictionarySetValue(query, kSecValueData, data)
                CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                status = SecItemAdd(query, null)
            }
            check(status == errSecSuccess) { "Could not save source session in Keychain ($status)" }
        } finally {
            CFRelease(update)
            CFRelease(data)
        }
    }

    private fun <T> query(key: String, use: (CFMutableDictionaryRef) -> T): T {
        val query = checkNotNull(CFDictionaryCreateMutable(null, 0, null, null))
        val service = checkNotNull(CFStringCreateWithCString(null, "app.koyura.source-sessions", kCFStringEncodingUTF8))
        val account = checkNotNull(CFStringCreateWithCString(null, key, kCFStringEncodingUTF8))
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, service)
            CFDictionarySetValue(query, kSecAttrAccount, account)
            return use(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
            CFRelease(account)
        }
    }
}
