package dev.reader.source.runtime

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
                displaced.delete(key);
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
        });
    })();
""".trimIndent()
