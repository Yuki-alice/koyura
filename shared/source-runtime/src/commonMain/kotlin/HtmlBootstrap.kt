package app.koyura.source.runtime

internal val htmlBootstrap = """
    const { HtmlDocument, HtmlElement, HtmlNode } = (() => {
        const dispatch = __readerHtml;
        function call(operation, document, node, value) {
            const result = JSON.parse(dispatch(JSON.stringify({ operation, document, node, value })));
            if (result.error !== undefined) throw new Error(result.error);
            return result.value;
        }
        function element(node, doc) {
            return node === null ? null : new HtmlElement(node, doc);
        }

        class HtmlDocument {
            constructor(html) { this.key = call("parse", null, null, html); }
            querySelector(query) { return element(call("queryFirst", this.key, null, query), this.key); }
            querySelectorAll(query) { return call("query", this.key, null, query).map(key => element(key, this.key)); }
            getElementById(id) { return element(call("byId", this.key, null, id), this.key); }
            dispose() { call("dispose", this.key); }
        }

        class HtmlElement {
            constructor(key, doc) { this.key = key; this.doc = doc; }
            querySelector(query) { return element(call("queryFirst", this.doc, this.key, query), this.doc); }
            querySelectorAll(query) { return call("query", this.doc, this.key, query).map(key => element(key, this.doc)); }
            get text() { return call("text", this.doc, this.key); }
            get attributes() { return call("attributes", this.doc, this.key); }
            get children() { return call("children", this.doc, this.key).map(key => element(key, this.doc)); }
            get nodes() { return call("nodes", this.doc, this.key).map(key => new HtmlNode(key, this.doc)); }
            get innerHTML() { return call("html", this.doc, this.key); }
            get innerHtml() { return this.innerHTML; }
            get parent() { return element(call("parent", this.doc, this.key), this.doc); }
            get classNames() { return call("classes", this.doc, this.key); }
            get id() { return call("id", this.doc, this.key); }
            get localName() { return call("name", this.doc, this.key); }
            get previousElementSibling() { return element(call("previous", this.doc, this.key), this.doc); }
            get nextElementSibling() { return element(call("next", this.doc, this.key), this.doc); }
        }

        class HtmlNode {
            constructor(key, doc) { this.key = key; this.doc = doc; }
            get text() { return call("text", this.doc, this.key); }
            get type() { return call("type", this.doc, this.key); }
            toElement() { return this.type === "element" ? element(this.key, this.doc) : null; }
        }

        return { HtmlDocument, HtmlElement, HtmlNode };
    })();
""".trimIndent()
