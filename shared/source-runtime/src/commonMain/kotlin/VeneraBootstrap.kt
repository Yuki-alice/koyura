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

        loadSetting(key) {
            return __readerVenera.readSourceValue(this, "loadSetting", key) ?? this.settings?.[key]?.default ?? null;
        }

        loadData(key) { return __readerVenera.readSourceValue(this, "loadData", key); }
        saveData(key, value) { __readerVenera.writeSourceValue(this, key, { value }); }
        deleteData(key) { __readerVenera.writeSourceValue(this, key, {}); }
        get isLogged() { return this.loadData("account") != null; }

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

    function Comment(fields) {
        Object.assign(this, fields);
    }

    function Cookie(fields) {
        Object.assign(this, fields);
    }

    function ImageLoadingConfig(fields) {
        Object.assign(this, fields);
    }

    const __readerVenera = (() => {
        const request = __readerRequest;
        const intervalHost = __readerInterval;
        delete globalThis.__readerRequest;
        delete globalThis.__readerInterval;
        const dispatchSession = __readerSourceSession;
        delete globalThis.__readerSourceSession;
        const sourceSessions = new WeakMap();

        function sessionRequest(key, operation, args) {
            const response = JSON.parse(dispatchSession(serializeResult({ key, operation, ...args })));
            if (response.error !== undefined) throw new Error(response.error);
            return response.value;
        }
        const sources = ComicSource.sources;
        let pending = null;
        let pendingKey = null;
        let sequence = 0;
        let intervalSequence = 0;
        const intervals = new Map();
        const seqBySource = new WeakMap();
        // Previous instance displaced by initializeReplace. Kept so a repair
        // can put the exact object (with its memory state) back when the
        // candidate never settles, e.g. its init await was abandoned.
        const displaced = new Map();

        function network(sendMessage, session) {
            return {
                setCookies(url, cookies) { session("setCookies", { url, cookies }); },
                getCookies(url) { return session("getCookies", { url }); },
                deleteCookies(url) { session("deleteCookies", { url }); },
                async fetchBytes(method, url, headers, data, extra) {
                    const result = await sendMessage({ method: "http", http_method: method, url, headers, data, extra, bytes: true });
                    if (result.error) throw result.error;
                    return result;
                },
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

        function fetchWith(net) {
            return async (url, options) => {
                const response = await net.fetchBytes(options?.method || "GET", url, options?.headers, options?.body);
                async function text() {
                    const decoded = Convert.decodeUtf8(response.body);
                    if (decoded === null) throw new TypeError("Response body is not valid UTF-8");
                    return decoded;
                }
                return {
                    ok: response.status >= 200 && response.status < 300,
                    status: response.status,
                    statusText: "",
                    headers: response.headers,
                    arrayBuffer: async () => response.body,
                    text,
                    async json() {
                        const value = await text();
                        try { return JSON.parse(value); }
                        catch (error) {
                            const safeUrl = String(url).split(/[?#]/, 1)[0];
                            const contentType = response.headers["content-type"] || "unknown";
                            throw new SyntaxError("Invalid JSON response from " + safeUrl + " (HTTP " +
                                response.status + ", Content-Type " + contentType + "): " + error.message);
                        }
                    },
                };
            };
        }

        function cancelInterval(id) {
            if (intervals.delete(id)) intervalHost(id + ":cancel");
        }

        function releaseSource(source) {
            sourceSessions.get(source)?.interaction.dispose();
            releaseIntervals(source);
        }

        function releaseIntervals(source) {
            for (const [id, timer] of intervals) if (timer.source === source) cancelInterval(id);
        }

        function field(source, path) {
            return path.split(".").reduce((parent, part) => parent?.[part], source);
        }

        function callback(source, path) {
            const parts = path.split(".");
            const name = parts.pop();
            const owner = parts.reduce((parent, part) => parent?.[part], source);
            return [owner, owner?.[name]];
        }

        function invokeCallback(owner, invoke, args, synchronous, path) {
            if (typeof invoke !== "function") throw new TypeError("Source callback is unavailable: " + path);
            const result = Reflect.apply(invoke, owner, args);
            if (synchronous) {
                if (typeof result?.then === "function") throw new TypeError(path + " must return synchronously");
                return serializeResult(result);
            }
            return Promise.resolve(result).then(serializeResult);
        }

        function serializeResult(result) {
            return JSON.stringify(result ?? null, (_, value) => {
                if (typeof value === "function" || value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
                    throw new TypeError("The callback returned a function or binary value where the host expects JSON");
                }
                return value instanceof Map ? Object.fromEntries(value) : value;
            });
        }

        function activate(source, key) {
            const slot = sourceSessions.get(source);
            slot.key = key;
        }

        function flushSourceData(source) {
            const slot = sourceSessions.get(source);
            if (Object.keys(slot.changes).length) sessionRequest(slot.key, "applyData", { changes: slot.changes });
            slot.changes = Object.create(null);
            slot.staged = false;
        }

        return Object.freeze({
            readSourceValue(source, operation, name) {
                const slot = sourceSessions.get(source);
                if (!slot?.key || sources[slot.key] !== source) return null;
                if (operation === "loadData" && Object.hasOwn(slot.changes, name)) {
                    return slot.changes[name].value ?? null;
                }
                if (operation !== "loadData" && operation !== "loadSetting") throw new Error("Invalid read operation");
                return sessionRequest(slot.key, operation, { name });
            },
            writeSourceValue(source, name, change) {
                const slot = sourceSessions.get(source);
                if (!slot?.key || sources[slot.key] !== source) throw new Error("Source instance is inactive");
                if (name === "setting") throw new Error("Setting values belong to source configuration");
                // Clone before retaining it so a later script mutation cannot rewrite saved data.
                const entry = JSON.parse(serializeResult(change));
                if (slot.staged) slot.changes[name] = entry;
                else sessionRequest(slot.key, "applyData", { changes: { [name]: entry } });
            },
            webLoginUrl(key) {
                const source = sources[key];
                return source?.account?.loginWithWebview?.url ?? null;
            },
            stage(construct, paths) {
                let source = null;
                let sourceKey = null;
                const slot = { key: null, staged: true, changes: Object.create(null) };
                const interaction = __readerInteractions.create(() => slot.key, () => slot.key !== null && sources[slot.key] === source);
                slot.interaction = interaction;
                const session = (operation, args) => {
                    if (!slot.key || sources[slot.key] !== source) throw new Error("Source instance is inactive");
                    return sessionRequest(slot.key, operation, args);
                };
                async function sendHttp(message) {
                    if (sourceKey !== null && (!slot.key || sources[slot.key] !== source)) {
                        throw new Error("Source instance is inactive");
                    }
                    const binary = __readerUtilities.isBytes(message.data);
                    const response = JSON.parse(await request(serializeResult({
                        bytes: message.bytes === true,
                        request: {
                            sourceKey,
                            method: message.http_method,
                            url: message.url,
                            headers: message.headers ?? {},
                            data: binary ? null : message.data,
                            binaryData: binary ? __readerUtilities.toBase64(message.data) : null,
                            extra: message.extra,
                        },
                    })));
                    if (response.error !== undefined) return response;
                    if (message.bytes) response.body = __readerUtilities.fromBase64(response.body);
                    return response;
                }
                const sendMessage = message => {
                    switch (message.method) {
                        case "http": return sendHttp(message);
                        case "cookie": {
                            const names = { get: "getCookies", set: "setCookies", delete: "deleteCookies" };
                            if (!Object.hasOwn(names, message.function)) throw new Error("Unknown Cookie operation");
                            return session(names[message.function], { url: message.url, cookies: message.cookies });
                        }
                        case "load_data": return source?.loadData(message.data_key) ?? null;
                        case "save_data": return source.saveData(message.data_key, message.data);
                        case "delete_data": return source.deleteData(message.data_key);
                        case "load_setting": return source?.loadSetting(message.setting_key) ?? null;
                        case "isLogged": return source?.isLogged ?? false;
                        case "log": return interaction.log(message.level, message.title, message.content);
                        case "setClipboard": return interaction.setClipboard(message.text);
                        case "getClipboard": return interaction.getClipboard();
                        case "UI": {
                            const ui = interaction.UI;
                            switch (message.function) {
                                case "showMessage": return ui.showMessage(message.message);
                                case "launchUrl": return ui.launchUrl(message.url);
                                case "showDialog": return ui.showDialog(message.title, message.content, message.actions);
                                case "showLoading": return ui.showLoading(message.onCancel);
                                case "cancelLoading": return ui.cancelLoading(message.id);
                                case "showInputDialog": return ui.showInputDialog(message.title, message.validator, message.image);
                                case "showSelectDialog": return ui.showSelectDialog(message.title, message.options, message.initialIndex);
                                default: throw new Error("Unknown source interaction");
                            }
                        }
                        case "getLocale": return APP.locale;
                        case "getPlatform": return APP.platform;
                        case "delay": return new Promise(resolve => setTimeout(resolve, message.time));
                        case "compute": return compute(message.function, ...(message.args ?? []));
                        default: throw new Error("Unsupported Venera host message: " + message.method);
                    }
                };
                function setInterval(callback, milliseconds) {
                    if (!slot.key || sources[slot.key] !== source) throw new Error("Source instance is inactive");
                    if (typeof callback !== "function") throw new TypeError("Interval callback is not a function");
                    if (intervals.size >= 128) throw new Error("Too many live intervals");
                    const id = String(++intervalSequence);
                    const duration = Math.max(1, Math.floor(Number(milliseconds) || 1));
                    intervals.set(id, { source, key: slot.key, callback });
                    try { callback(); }
                    catch (error) { cancelInterval(id); throw error; }
                    intervalHost(id + ":" + duration);
                    return { cancel: () => cancelInterval(id) };
                }
                const net = network(sendMessage, session);
                source = construct(net, sendMessage, fetchWith(net), setInterval,
                    interaction.UI, interaction.setClipboard, interaction.getClipboard, interaction.log, interaction.console);
                if (!(source instanceof ComicSource)) throw new TypeError("Expected a ComicSource instance");
                seqBySource.set(source, ++sequence);
                sourceSessions.set(source, slot);

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
                activate(pending, key);
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
                activate(pending, key);
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
            async fireInterval(id) {
                const timer = intervals.get(id);
                if (timer === undefined) return false;
                if (sources[timer.key] !== timer.source) return displaced.get(timer.key) === timer.source;
                if (sourceSessions.get(timer.source).staged) return true;
                try {
                    await timer.callback();
                    return intervals.has(id);
                } catch (error) {
                    cancelInterval(id);
                    return false;
                }
            },
            repairDisplaced(key) {
                if (!displaced.has(key)) return false;
                releaseSource(sources[key]);
                const prev = displaced.get(key);
                displaced.delete(key);
                if (prev === undefined) delete sources[key];
                else sources[key] = prev;
                return true;
            },
            commit() {
                if (pending && !displaced.has(pendingKey)) flushSourceData(pending);
                pending = null;
                pendingKey = null;
            },
            discard() {
                releaseSource(pending);
                pending = null;
                pendingKey = null;
            },
            rollback() {
                releaseSource(pending);
                if (pendingKey !== null) delete sources[pendingKey];
                pending = null;
                pendingKey = null;
            },
            unregister(key) {
                releaseSource(sources[key]);
                releaseSource(displaced.get(key));
                delete sources[key];
                displaced.delete(key);
            },
            finishReplacement(key) {
                if (displaced.has(key) && sources[key]) flushSourceData(sources[key]);
                releaseSource(displaced.get(key));
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
                return invokeCallback(owner, invoke, args, synchronous, path);
            },
            invokeIndexed(key, section, index, member, args, synchronous) {
                const source = sources[key];
                if (source === undefined) throw new Error("Source is not registered: " + key);
                const owner = field(source, section)?.[index];
                return invokeCallback(owner, owner?.[member], args, synchronous, section + "." + member);
            },
            invokeSetting(key, name) {
                const source = sources[key];
                const settings = source?.settings;
                const setting = settings && Object.hasOwn(settings, name) ? settings[name] : undefined;
                if (setting?.type !== "callback") throw new Error("Source setting has no callback");
                return invokeCallback(setting, setting.callback, [], false, "settings." + name);
            },
            describe(key, path) {
                const source = sources[key];
                if (source === undefined) throw new Error("Source is not registered: " + key);
                const value = field(source, path);
                if (path === "explore") {
                    return serializeResult((value ?? []).map((page, index) => ({
                        index, title: page.title, type: page.type,
                        numbered: typeof page.load === "function",
                        cursor: typeof page.loadNext === "function",
                    })));
                }
                return JSON.stringify(value ?? null, (_, item) => typeof item === "function" ? undefined : item);
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
                    if (result.modifyImage != null && typeof result.modifyImage !== "string") {
                        throw new TypeError("Image config field is not a string: modifyImage");
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
