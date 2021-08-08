package org.gamboni.cloudspill.server.html.js;

import com.google.common.base.Preconditions;

import org.gamboni.cloudspill.server.config.BackendConfiguration;
import org.gamboni.cloudspill.shared.api.CloudSpillApi;
import org.gamboni.cloudspill.shared.api.StaticResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author tendays
 */
public abstract class AbstractJs implements StaticResource {

    @Override
    public String getExtension() {
        return "js";
    }

    /** A set of variable and function names */
    private static class Scope {
        final Scope outer;
        final Set<String> vars = new HashSet<>();

        Scope() {
            this.outer = null;
        }

        Scope(Scope outer) {
            this.outer = outer;
        }

        public String newName(String base) {
            String candidate = base;
            int counter = 1;
            while (!isFree(candidate)) {
                counter++;
                candidate = base + counter;
            }
            return candidate;
        }

        boolean isFree(String name) {
            return !vars.contains(name) && (outer == null || outer.isFree(name));
        }
    }

    protected final CloudSpillApi<JsCallback> remoteApi;
    private StringBuilder buffer;
    private String indent = "";
    private String value = null;
    private final Scope topScope = new Scope();

    private JsFunction currentFunction = null;

    protected static abstract class JsCallback {

        private final Map<String, String> headers = new LinkedHashMap<>();

        public abstract void run(String req);

        public String getBody() {
            return "";
        }

        public JsCallback withHeader(String name, String value) {
            headers.put(name, value);
            return this;
        }
    }

    /** Send a query with something in request body: api.someCall(params, send(body, () -> {response handler})) */
    protected JsCallback send(String body, Runnable callback) {
        return new JsCallback() {
            @Override
            public void run(String req) {
                callback.run();
            }

            @Override
            public String getBody() {
                return body;
            }
        };
    }

    /** Send a query with something in request body: api.someCall(params, send(body, request -> {response handler})) */
    protected JsCallback send(String body, Consumer<String> callback) {
        return new JsCallback() {
            @Override
            public void run(String req) {
                callback.accept(req +".responseText");
            }

            @Override
            public String getBody() {
                return body;
            }
        };
    }

    public AbstractJs(BackendConfiguration configuration) {
        this.remoteApi = new CloudSpillApi<>(configuration.getPublicUrl(), (method, url, consumer) -> {
            String req = let("req", "new XMLHttpRequest()");
            appendLine(req + ".onreadystatechange = () => {");
            indented(() -> {
                appendLine("if (" + req + ".readyState != 4) return;");
                consumer.run(req);
            });
            appendLine("};");
            // TODO how to properly escape the url? (Normally should not be needed).
            // NOTE: it may contain template expressions and by the time this method is called we don't know
            // if those are real or injected in the server configuration
            appendLine(req + ".open(" + lit(method.name()) + ", `" + url + "`);");
            consumer.headers.forEach((header, value) -> {
                appendLine(req +".setRequestHeader("+ lit(header) +","+ value+");");
            });
            appendLine(req + ".send(" + consumer.getBody() + ");");
        });
    }

    protected String lit(String string) {
        return "'"+ string
                .replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("'", "\\'") +"'";
    }

    protected String let(String baseName, String value) {
        String name = getCurrentScope().newName(baseName);
        appendLine("let "+ name +"="+ value +";");
        return name;
    }

    protected abstract void build();

    private static class JsFunction {
        final String name;
        final List<String> params = new ArrayList<>();
        final Scope scope;

        private JsFunction(String name, Scope outerScope) {
            this.name = name;
            this.scope = new Scope(outerScope);
        }

        public String signatureLine() {
            return "function "+ name +"("+ String.join(", ", params) +") {";
        }
    }

    protected Scope getCurrentScope() {
        return (currentFunction == null) ? this.topScope : currentFunction.scope;
    }

    protected void function(String baseName, Runnable body) {
        JsFunction outerFunction = this.currentFunction;
        this.currentFunction = new JsFunction(getCurrentScope().newName(baseName), getCurrentScope());

        StringBuilder innerBuffer = new StringBuilder();
        StringBuilder outerBuffer = this.buffer;

        this.buffer = innerBuffer;
        indented(body); // will write into innerBuffer
        this.buffer = outerBuffer;

        appendLine(currentFunction.signatureLine());
        outerBuffer.append(innerBuffer);
        appendLine("}");

        this.currentFunction = outerFunction;
    }

    protected String param() {
        return param("arg");
    }

    protected String param(String baseName) {
        Preconditions.checkState(currentFunction != null);
        final String name = currentFunction.scope.newName(baseName);
        currentFunction.params.add(name);
        return name;
    }

    private void indented(Runnable body) {
        String previousIndent = indent;
        indent += "  ";
        body.run();
        indent = previousIndent;
    }

    protected void appendLine(String text) {
        buffer.append(indent).append(text).append('\n');
    }


    public String toString() {
        /* Calling toString() while the value is being constructed just returns the current status */
        if (buffer != null) {
            return buffer.toString();
        }

        if (value == null) {
            buffer = new StringBuilder();
            build();
            value = buffer.toString();
        }
        return value;
    }
}
