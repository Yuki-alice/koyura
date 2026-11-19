package app.koyura.source.runtime

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ConvertCompatibilityTest {
    @Test
    fun matchesEncodingDigestHmacAesAndRsaVectorsThroughScriptBindings() = runTest {
        val runtime = VeneraSourceRuntime.create(VeneraEnvironment("1.16.0", "en", "test")) {
            error("Unexpected HTTP")
        }
        try {
            runtime.register(script)
            val result = runtime.call("convertvectors", VeneraCallback.LoadInfo).jsonObject
            val expected = mapOf(
                "utf8" to "中🙂",
                "utf8Hex" to "e4b8adf09f9982",
                "gbk" to "中文",
                "gbkHex" to "d6d0cec4",
                "base64" to "AP8B",
                "binary" to "00ff01",
                "md5" to "900150983cd24fb0d6963f7d28e17f72",
                "sha1" to "a9993e364706816aba3e25717850c26c9cd0d89d",
                "sha256" to "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                "sha512" to "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a219" +
                    "2992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f",
                "hmacMd5" to "d2fe98063f876b03193afb49b4979591",
                "hmacSha1" to "4fd0b215276ef12f2b3e4c8ecac2811498b656fc",
                "hmacSha256" to "9c196e32dc0175f86f4b1cb89289d6619de6bee699e4c378e68309ed97a1a6ab",
                "hmacSha512" to "3926a207c8c42b0c41792cbd3e1a1aaaf5f7a25704f62dfc939c4987dd7ce060" +
                    "009c5bb1c2447355b3216f10b537e9afa7b64a4e5391b0d631172d07939e087a",
                "ecb" to "3ad77bb40d7a3660a89ecaf32466ef97",
                "cbc" to "7649abac8119b246cee98e9b12e9197d",
                "cfb" to "3b3fd92eb72dad20333449f8e83cfb4a",
                "ofb" to "1636d5ee34f80625d77f8e56ca884345",
                "decryptEcb" to plain,
                "decryptCbc" to plain,
                "decryptCfb" to plain,
                "decryptOfb" to plain,
                "rsa" to "source-compatibility",
            )
            for ((name, value) in expected) assertEquals(JsonPrimitive(value), result[name], name)
            assertEquals(JsonNull, result["badInput"])
        } finally {
            runtime.close()
        }
    }

    private val plain = "6bc1bee22e409f96e93d7e117393172a"
    private val script = """
        class Source extends ComicSource {
            name = "Convert vectors"; key = "convertvectors"; version = "1.0.0"; minAppVersion = "1.0.0";
            comic = { loadInfo: () => {
                const c = Convert;
                const hex = c.hexEncode;
                const utf8 = c.encodeUtf8("中🙂");
                const gbk = c.encodeGbk("中文");
                const binary = c.hexDecode("00ff01");
                const text = c.encodeUtf8("abc");
                const hmacKey = c.encodeUtf8("key");
                const key = c.hexDecode("2b7e151628aed2a6abf7158809cf4f3c");
                const iv = c.hexDecode("000102030405060708090a0b0c0d0e0f");
                const block = c.hexDecode("$plain");
                const ecb = c.encryptAesEcb(block, key);
                const cbc = c.encryptAesCbc(block, key, iv);
                const cfb = c.encryptAesCfb(block, key, iv, 128);
                const ofb = c.encryptAesOfb(block, key, 128);
                const ciphertext = c.decodeBase64("$rsaCiphertext");
                return {
                    utf8: c.decodeUtf8(utf8), utf8Hex: hex(utf8), gbk: c.decodeGbk(gbk), gbkHex: hex(gbk),
                    base64: c.encodeBase64(binary), binary: hex(c.decodeBase64("AP8B")),
                    md5: hex(c.md5(text)), sha1: hex(c.sha1(text)), sha256: hex(c.sha256(text)), sha512: hex(c.sha512(text)),
                    hmacMd5: hex(c.hmac(hmacKey, text, "md5")), hmacSha1: hex(c.hmac(hmacKey, text, "sha1")),
                    hmacSha256: c.hmacString(hmacKey, text, "sha256"), hmacSha512: c.hmacString(hmacKey, text, "sha512"),
                    ecb: hex(ecb), cbc: hex(cbc), cfb: hex(cfb), ofb: hex(ofb),
                    decryptEcb: hex(c.decryptAesEcb(ecb, key)), decryptCbc: hex(c.decryptAesCbc(cbc, key, iv)),
                    decryptCfb: hex(c.decryptAesCfb(cfb, key, iv, 128)), decryptOfb: hex(c.decryptAesOfb(ofb, key, 128)),
                    rsa: c.decodeUtf8(c.decryptRsa(ciphertext, "$rsaKey")),
                    badInput: c.decryptAesEcb(c.hexDecode("00"), key),
                };
            }};
        }
    """.trimIndent()

    private companion object {
        // Throwaway PKCS#8 key and ciphertext generated solely for this test vector.
        val rsaKey = """
            MIICdwIBADANBgkqhkiG9w0BAQEFAASCAmEwggJdAgEAAoGBANKUw2hZOXeH52HZL9zZEPalnFOKK+dw0wjrtwac
            oPdd2SZgf+DlryPQ07YHX6zgXlsyOSPIILMxwaEMWETJbh3/yXvHoXGf+NH419cPi+cTJt9A7JHj0kZC4h/oQO2I
            ghG0aGbfvJPENYa0biDAN+cLbJr+xUGX4tPe6KsfY5mzAgMBAAECgYBr7h9xfjm8EAYB2sVzVjYhlKRZFPqHtwYT
            Dm1SkDjYUFuaFd+1L0PjAYdhRso82wrBhy5OLqbD8X6iwqcDKpKrEox6v+Ka2xymdXNXaxpfxOtQJYhBXmOwJ2dl
            U7iKgty/nz+7nLD82xL7kKscpZpeFKBDvGYCRlol30aXut0uwQJBAP5n02z93OwxtcSyVwT9o+qNssLqh4Hf/SnO
            y/yIcQw/ApYDi3ULGmjwDyOHwkcbds9M8o3c4FeLzCz91JPCWhECQQDT5p/ccz2nW9nd82/N7/YBej0GJu+kYYmQ
            4tH4Bb/Y3dgjeYUAyGrr2VS5WUIKRjC94a29jLSgMpk0no5n/FODAkEAizQciALQo5z/rMU4wE5ZJrqqGeViiDob
            uIijLWcrFFxV+eeuoziUFt9kKTjboTTx6bRc9CrrxnuEGQo7Cy7JQQJAdBxwRnTjOLxs4mDNR9QQQfkATUmykKld
            nwWatkQ4PpY5JB8nUSmA5SFeen29YTHa7YEnGbFrHEj9iEbFk5dBDwJBAPTwQ4JFaFUPTO/TyhaOck+yV+3Fa7BP
            07bc8M6EdS74Mywkb6gedh1RdiOOfcVwJ/Jlo/QyypFsmzUd1hfpLW8=
        """.filterNot(Char::isWhitespace)
        val rsaCiphertext = """
            qWOwfiID7HWE7pBp+tBiPpX4rZ/+1xQddFoXlKcoIx6XcTS0gSBJPPuNo7EwXGqn4VIfMK/vfjYt91syR1lp9IDy
            eSOGdavOhUFsVyOn4CvRU5cx4uc1H/2wk0ttvk6j9M4kHOPQQkGjWzFCd6/LCiIkemOQ+U5VmAFPGUF854E=
        """.filterNot(Char::isWhitespace)
    }
}
