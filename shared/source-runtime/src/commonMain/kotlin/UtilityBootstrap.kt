package app.koyura.source.runtime

internal val utilityBootstrap = """
    const __readerUtilities = (() => {
        const convert = __readerConvert;
        delete globalThis.__readerConvert;
        const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        const isBytes = value => value instanceof ArrayBuffer || ArrayBuffer.isView(value);
        function bytes(value) {
            return ArrayBuffer.isView(value)
                ? new Uint8Array(value.buffer, value.byteOffset, value.byteLength)
                : new Uint8Array(value);
        }
        function toBase64(value) {
            const input = bytes(value);
            let result = "";
            for (let i = 0; i < input.length; i += 3) {
                const chunk = input[i] << 16 | (input[i + 1] ?? 0) << 8 | (input[i + 2] ?? 0);
                result += alphabet[chunk >> 18] + alphabet[chunk >> 12 & 63] +
                    (i + 1 < input.length ? alphabet[chunk >> 6 & 63] : "=") +
                    (i + 2 < input.length ? alphabet[chunk & 63] : "=");
            }
            return result;
        }
        function fromBase64(input) {
            const size = input.length * 3 / 4 - (input.endsWith("==") ? 2 : input.endsWith("=") ? 1 : 0);
            const result = new Uint8Array(size);
            for (let i = 0, j = 0; i < input.length; i += 4) {
                const chunk = alphabet.indexOf(input[i]) << 18 | alphabet.indexOf(input[i + 1]) << 12 |
                    Math.max(alphabet.indexOf(input[i + 2]), 0) << 6 | Math.max(alphabet.indexOf(input[i + 3]), 0);
                result[j++] = chunk >> 16;
                if (j < size) result[j++] = chunk >> 8;
                if (j < size) result[j++] = chunk;
            }
            return result.buffer;
        }
        function call(operation, value, extra = {}) {
            const answer = JSON.parse(convert(JSON.stringify({ operation, value, ...extra })));
            if (answer == null) return null;
            return answer.bytes === undefined ? answer.text : fromBase64(answer.bytes);
        }
        function crypt(operation, encrypt, value, key, iv, blockSize) {
            return call(operation, toBase64(value), { key: toBase64(key), iv: iv == null ? null : toBase64(iv), encrypt, blockSize });
        }
        function pack(value) {
            if (isBytes(value)) return ["bytes", toBase64(value)];
            if (Array.isArray(value)) return ["array", value.map(pack)];
            if (value instanceof Map) return ["object", [...value].map(([key, child]) => [String(key), pack(child)])];
            if (value !== null && typeof value === "object") return ["object", Object.entries(value).map(([k, v]) => [k, pack(v)])];
            if (typeof value === "function" || typeof value === "symbol" || typeof value === "bigint") {
                throw new TypeError("Cannot transfer value to or from compute");
            }
            return ["value", value ?? null];
        }
        function unpack([type, value]) {
            if (type === "bytes") return fromBase64(value);
            if (type === "array") return value.map(unpack);
            if (type === "object") return Object.fromEntries(value.map(([key, child]) => [key, unpack(child)]));
            return value;
        }
        return Object.freeze({ isBytes, toBase64, fromBase64, call, crypt, pack, unpack });
    })();

    const Convert = (() => {
        const { call, toBase64, crypt } = __readerUtilities;
        return Object.freeze({
            encodeUtf8: value => call("utf8.encode", String(value)),
            decodeUtf8: value => call("utf8.decode", toBase64(value)),
            encodeGbk: value => call("gbk.encode", String(value)),
            decodeGbk: value => call("gbk.decode", toBase64(value)),
            encodeBase64: value => call("base64.encode", toBase64(value)),
            decodeBase64: value => call("base64.decode", String(value)),
            hexEncode: value => call("hex.encode", toBase64(value)),
            hexDecode: value => call("hex.decode", String(value)),
            md5: value => call("md5", toBase64(value)),
            sha1: value => call("sha1", toBase64(value)),
            sha256: value => call("sha256", toBase64(value)),
            sha512: value => call("sha512", toBase64(value)),
            hmac: (key, value, hash) => call("hmac", toBase64(value), { key: toBase64(key), hash }),
            hmacString: (key, value, hash) => call("hmacString", toBase64(value), { key: toBase64(key), hash }),
            encryptAesEcb: (value, key) => crypt("aes-ecb", true, value, key),
            decryptAesEcb: (value, key) => crypt("aes-ecb", false, value, key),
            encryptAesCbc: (value, key, iv) => crypt("aes-cbc", true, value, key, iv),
            decryptAesCbc: (value, key, iv) => crypt("aes-cbc", false, value, key, iv),
            encryptAesCfb: (value, key, iv, size) => crypt("aes-cfb", true, value, key, iv, size),
            decryptAesCfb: (value, key, iv, size) => crypt("aes-cfb", false, value, key, iv, size),
            encryptAesOfb: (value, key, size) => crypt("aes-ofb", true, value, key, null, size),
            decryptAesOfb: (value, key, size) => crypt("aes-ofb", false, value, key, null, size),
            decryptRsa: (value, key) => call("rsa", toBase64(value), { key: typeof key === "string" ? key : toBase64(key) }),
        });
    })();

    function createUuid() { return __readerUtilities.call("uuid", ""); }
    function randomDouble(min, max) { return min + Math.random() * (max - min); }
    function randomInt(min, max) { return Math.floor(randomDouble(min, max)); }
""".trimIndent()

internal val mainAsyncBootstrap = """
    const compute = (() => {
        const worker = __readerCompute;
        delete globalThis.__readerCompute;
        return async function compute(code, ...args) {
            if (typeof code !== "string") throw new TypeError("compute expects a function string");
            const response = JSON.parse(await worker(JSON.stringify({ code, args: __readerUtilities.pack(args) })));
            if (response.error !== undefined) throw new Error(response.error);
            return __readerUtilities.unpack(response.value);
        };
    })();

    const setTimeout = (() => {
        const wait = __readerDelay;
        delete globalThis.__readerDelay;
        return function setTimeout(callback, milliseconds) {
            wait(String(Math.max(0, Math.floor(Number(milliseconds) || 0)))).then(callback);
        };
    })();
""".trimIndent()
