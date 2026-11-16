package app.koyura.source.runtime

internal val veneraBootstrap = """
    class ComicSource {
        static sources = Object.create(null);
        name = "";
        key = "";
        version = "";
        minAppVersion = "";
        url = "";
        translation = {};

        translate(text) {
            return this.translation[APP.locale]?.[text] ?? text;
        }
    }

    function Comic(fields) {
        Object.assign(this, fields);
    }

    function ComicDetails({ subTitle, subtitle = subTitle, ...fields }) {
        Object.assign(this, fields, { subtitle: subtitle ?? subTitle });
    }

    function ImageLoadingConfig(fields) {
        Object.assign(this, fields);
    }

    const __readerVenera = (() => {
        const requestText = __readerRequestText;
        const sources = ComicSource.sources;
        let pending = null;
        let pendingKey = null;
        let sequence = 0;
        const seqBySource = new WeakMap();
        // Previous instance displaced by initializeReplace. Kept so a repair
        // can put the exact object (with its memory state) back when the
        // candidate never settles, e.g. its init await was abandoned.
        const displaced = new Map();

        function network(sendMessage) {
            return {
                async sendRequest(method, url, headers, data, extra) {
                    const result = await sendMessage({ method: "http", http_method: method, url, headers, data, extra });
                    if (result.error) throw result.error;
                    return result;
                },
                get(url, headers, extra) {
                    return this.sendRequest("GET", url, headers, null, extra);
                },
                delete(url, headers, extra) {
                    return this.sendRequest("DELETE", url, headers, null, extra);
                },
                post(url, headers, data, extra) {
                    return this.sendRequest("POST", url, headers, data, extra);
                },
                put(url, headers, data, extra) {
                    return this.sendRequest("PUT", url, headers, data, extra);
                },
                patch(url, headers, data, extra) {
                    return this.sendRequest("PATCH", url, headers, data, extra);
                },
            };
        }

        function callback(source, path) {
            const [section, name] = path.split(".");
            const owner = source[section];
            return [owner, owner?.[name]];
        }

        function serializeResult(result) {
            return JSON.stringify(result ?? null, (_, value) => {
                if (typeof value === "function" || value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
                    throw new TypeError("The callback returned a function or binary value where the host expects JSON");
                }
                return value;
            });
        }

        return Object.freeze({
            stage(construct, paths) {
                let sourceKey = null;
                const sendMessage = async message => {
                    if (message.method !== "http" || message.bytes) {
                        throw new Error("Unsupported Venera host message: " + message.method);
                    }
                    return JSON.parse(await requestText(serializeResult({
                        sourceKey,
                        method: message.http_method,
                        url: message.url,
                        headers: message.headers ?? {},
                        data: message.data,
                        extra: message.extra,
                    })));
                };
                const source = construct(network(sendMessage), sendMessage);
                if (!(source instanceof ComicSource)) throw new TypeError("Expected a ComicSource instance");
                seqBySource.set(source, ++sequence);

                const { name, key, version, minAppVersion, url } = source;
                sourceKey = key;
                const callbacks = paths.filter(path => typeof callback(source, path)[1] === "function");
                pending = source;
                return serializeResult({ name, key, version, minAppVersion, url, callbacks });
            },
            async initialize(key) {
                if (pending === null || pending.key !== key || Object.hasOwn(sources, key)) {
                    throw new Error("Source cannot be initialized: " + key);
                }
                pendingKey = key;
                sources[key] = pending;
                if (typeof pending.init === "function") await pending.init();
                if (sources[key] !== pending) throw new Error("Source changed its registry entry during init: " + key);
                return true;
            },
            async initializeReplace(key) {
                if (pending === null || pending.key !== key) {
                    throw new Error("Source cannot be initialized: " + key);
                }
                const prev = Object.hasOwn(sources, key) ? sources[key] : undefined;
                displaced.set(key, prev);
                pendingKey = key;
                sources[key] = pending;
                try {
                    if (typeof pending.init === "function") await pending.init();
                } catch (e) {
                    if (sources[key] === pending) {
                        if (prev === undefined) delete sources[key];
                        else sources[key] = prev;
                        displaced.delete(key);
                    }
                    throw e;
                }
                if (sources[key] !== pending) throw new Error("Source changed its registry entry during init: " + key);
                // NOTE: the displaced entry is intentionally kept after success.
                // A later DB-commit failure can still put the exact previous
                // instance back via repairDisplaced; the next replace overwrites it.
                return true;
            },
            repairDisplaced(key) {
                if (!displaced.has(key)) return false;
                const prev = displaced.get(key);
                displaced.delete(key);
                if (prev === undefined) delete sources[key];
                else sources[key] = prev;
                return true;
            },
            commit() {
                pending = null;
                pendingKey = null;
            },
            discard() {
                pending = null;
                pendingKey = null;
            },
            rollback() {
                if (pendingKey !== null) delete sources[pendingKey];
                pending = null;
                pendingKey = null;
            },
            unregister(key) {
                delete sources[key];
                displaced.delete(key);
            },
            finishReplacement(key) {
                displaced.delete(key);
            },
            instanceSeq(key) {
                const source = sources[key];
                if (source === undefined) return null;
                return seqBySource.has(source) ? seqBySource.get(source) : null;
            },
            invoke(key, path, args, synchronous) {
                const source = sources[key];
                if (source === undefined) throw new Error("Source is not registered: " + key);
                const [owner, invoke] = callback(source, path);
                if (typeof invoke !== "function") throw new TypeError("Source callback is unavailable: " + path);
                const result = Reflect.apply(invoke, owner, args);
                if (synchronous) {
                    if (typeof result?.then === "function") {
                        throw new TypeError(path + " must return synchronously");
                    }
                    return serializeResult(result);
                }
                return Promise.resolve(result).then(serializeResult);
            },
            invokeImage(configId, key, path, args, capturable) {
                const source = sources[key];
                if (source === undefined) throw new Error("Source is not registered: " + key);
                const [owner, invoke] = callback(source, path);
                if (typeof invoke !== "function") throw new TypeError("Source callback is unavailable: " + path);
                return __readerImage.capture(configId, capturable, () => Reflect.apply(invoke, owner, args));
            },
        });
    })();

    const __readerImage = (() => {
            // Function handles keep JS callbacks callable across the JSON-only
            // boundary: the host receives integer ids, never function objects.
            // Each tracked function gets a fresh id, so releasing one config
            // can never free another config's callbacks.
            let nextHandle = 1;
            const byId = new Map();
            const byConfig = new Map();

            function track(configId, fn) {
                const id = nextHandle++;
                byId.set(id, { configId, fn });
                byConfig.get(configId).add(id);
                return id;
            }

            function get(id) {
                const handle = byId.get(id);
                if (handle === undefined) throw new Error("Image callback was released: " + id);
                return handle.fn;
            }

            function discardConfig(configId) {
                const ids = byConfig.get(configId);
                if (ids === undefined) return;
                byConfig.delete(configId);
                for (const id of ids) byId.delete(id);
            }
            function serializePlain(result) {
                return JSON.stringify(result ?? null, (_, value) => {
                    if (typeof value === "function" || value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
                        throw new TypeError("The image config returned a function or binary value where the host expects JSON");
                    }
                    return value;
                });
            }
            function normalizeBytesOut(out) {
                if (out instanceof Uint8Array) return out.slice();
                if (out instanceof ArrayBuffer) return new Uint8Array(out);
                if (Array.isArray(out)) {
                    for (const v of out) {
                        if (typeof v !== "number" || !Number.isInteger(v) || v < 0 || v > 255) {
                            throw new TypeError("Image bytes must be a Uint8Array or ArrayBuffer, got array with non-byte entry");
                        }
                    }
                    return Uint8Array.from(out);
                }
                if (typeof out === "object" && out !== null && typeof out.buffer === "object") {
                    return new Uint8Array(out.buffer, out.byteOffset ?? 0, out.byteLength ?? 0).slice();
                }
                throw new TypeError("Image bytes must be a Uint8Array or ArrayBuffer, got " + (out === null ? "null" : typeof out));
            }
            return Object.freeze({
                async capture(configId, capturable, invoke, allowMissing = false) {
                    const ids = new Set();
                    byConfig.set(configId, ids);
                    try {
                        const result = await invoke();
                        if (!byConfig.has(configId)) throw new Error("Image config was discarded");
                        if (allowMissing && result == null) return "null";
                        return this.prepare(configId, result, capturable);
                    } catch (failure) {
                        discardConfig(configId);
                        throw failure;
                    } finally {
                        if (ids.size === 0) byConfig.delete(configId);
                    }
                },
                prepare(configId, result, capturable) {
                    if (result === null || typeof result !== "object" || Array.isArray(result)) {
                        throw new TypeError("Image config must be an object");
                    }
                    if (result.modifyImage != null) {
                        throw new Error("Unsupported image capability: modifyImage");
                    }
                    if (result.url !== undefined && result.url !== null && typeof result.url !== "string") {
                        throw new TypeError("Image config field is not a string: url");
                    }
                    if (result.method !== undefined && result.method !== null && typeof result.method !== "string") {
                        throw new TypeError("Image config field is not a string: method");
                    }
                    if (result.headers !== undefined && result.headers !== null) {
                        if (typeof result.headers !== "object" || Array.isArray(result.headers)) {
                            throw new TypeError("Image config headers must be an object: headers");
                        }
                        for (const name of Object.keys(result.headers)) {
                            if (typeof result.headers[name] !== "string") {
                                throw new TypeError("Image config header is not a string: " + name);
                            }
                        }
                    }
                    const handles = {};
                    const plain = {};
                    for (const name of Object.keys(result)) {
                        const value = result[name];
                        if (capturable.includes(name)) {
                            if (typeof value === "function") {
                                handles[name] = track(configId, value);
                                continue;
                            }
                            if (value !== undefined && value !== null) {
                                throw new TypeError("Image config field is not a function: " + name);
                            }
                            continue;
                        }
                        plain[name] = value;
                    }
                    return serializePlain({ data: plain, handles });
                },
                discardConfig,
                async callOnResponse(id, bytes) {
                    const fn = get(id);
                    const out = await fn(new Uint8Array(bytes));
                    return normalizeBytesOut(out);
                },
                callOnLoadFailed(configId, id, capturable) {
                    return this.capture(configId, capturable, () => get(id)(), true);
                },
                release(id) {
                    const handle = byId.get(id);
                    if (handle === undefined) return false;
                    byId.delete(id);
                    const ids = byConfig.get(handle.configId);
                    ids.delete(id);
                    if (ids.size === 0) byConfig.delete(handle.configId);
                    return true;
                },
                liveCount() {
                    return byId.size;
                },
            });
        })();
""".trimIndent()
