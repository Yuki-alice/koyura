package app.koyura.source.runtime

internal val interactionBootstrap = """
    const __readerInteractions = (() => {
        const send = __readerUiPost;
        const call = __readerUiCall;
        delete globalThis.__readerUiPost;
        delete globalThis.__readerUiCall;
        let nextId = 0;
        const callbacks = new Map();
        const loadings = new Map();

        return Object.freeze({
            async callback(id, value) {
                const action = callbacks.get(id);
                if (!action) return "null";
                const response = await action(value);
                return JSON.stringify(response == null ? null : String(response));
            },
            async cancel(id) {
                const cancel = loadings.get(id);
                if (cancel) await cancel();
            },
            create(keyFor, live) {
                const owned = new Set();
                const pendingCallbacks = new Set();
                function ensureLive() {
                    if (!live()) throw new Error("Source instance is inactive");
                }
                function post(type, args) {
                    if (type !== "log") ensureLive();
                    send(JSON.stringify({ type, key: keyFor(), ...args }));
                }
                async function interact(type, args) {
                    ensureLive();
                    return JSON.parse(await call(JSON.stringify({ type, key: keyFor(), ...args })));
                }
                function keep(fn) {
                    if (typeof fn !== "function") return null;
                    const id = ++nextId;
                    callbacks.set(id, value => { ensureLive(); return fn(value); });
                    pendingCallbacks.add(id);
                    return id;
                }
                function forget(id) {
                    callbacks.delete(id);
                    pendingCallbacks.delete(id);
                }
                function cancelLoading(id) {
                    if (!owned.delete(id)) return;
                    loadings.delete(id);
                    send(JSON.stringify({ type: "cancelLoading", key: keyFor(), id }));
                }
                const log = (level, title, content) => {
                    try { post("log", { level: String(level), title: String(title), content: String(content) }); }
                    catch (_) {}
                };
                return {
                    dispose() {
                        for (const id of owned) {
                            try { cancelLoading(id); } catch (_) {}
                        }
                        for (const id of pendingCallbacks) forget(id);
                    },
                    log,
                    console: Object.freeze({
                        log: content => log("info", "JS Console", content),
                        warn: content => log("warning", "JS Console", content),
                        error: content => log("error", "JS Console", content),
                    }),
                    setClipboard: text => interact("setClipboard", { content: String(text) }).then(() => undefined),
                    getClipboard: () => interact("getClipboard"),
                    UI: Object.freeze({
                        showMessage: message => post("message", { content: String(message) }),
                        launchUrl: url => post("launch", { content: String(url) }),
                        showDialog(title, content, actions) {
                            ensureLive();
                            const entries = (actions ?? []).map(action => ({
                                text: String(action.text), style: action.style ?? "text", callback: keep(action.callback),
                            }));
                            interact("dialog", { title, content, actions: entries })
                                .catch(error => log("error", "Dialog", String(error)))
                                .finally(() => entries.forEach(({callback}) => forget(callback)));
                        },
                        showLoading(onCancel) {
                            const id = ++nextId;
                            const cancellable = typeof onCancel === "function";
                            post("loading", { id, cancellable });
                            owned.add(id);
                            if (cancellable) loadings.set(id, async () => {
                                cancelLoading(id);
                                if (live()) await onCancel();
                            });
                            return id;
                        },
                        cancelLoading,
                        async showInputDialog(title, validator, image) {
                            const callback = keep(validator);
                            try {
                                return await interact("input", {
                                    title, callback, imageUrl: typeof image === "string" ? image : null,
                                    imageBytes: __readerUtilities.isBytes(image) ? __readerUtilities.toBase64(image) : null,
                                });
                            } finally { forget(callback); }
                        },
                        showSelectDialog: (title, options, initialIndex) =>
                            interact("select", { title, options, initialIndex: initialIndex ?? null }),
                    }),
                };
            },
        });
    })();
""".trimIndent()
