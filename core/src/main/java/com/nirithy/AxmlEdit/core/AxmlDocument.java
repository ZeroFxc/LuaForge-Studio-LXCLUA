package com.nirithy.AxmlEdit.core;

import java.util.ArrayList;
import java.util.List;

public class AxmlDocument extends Visitor {

    public List<AxmlNode> firsts = new ArrayList<AxmlNode>();
    public List<Namespace> nses = new ArrayList<Namespace>();

    public void accept(final Visitor visitor) {
        for (Namespace ns : nses) {
            ns.accept(visitor);
        }
        for (AxmlNode first : firsts) {
            first.accept(visitor);
        }
    }

    @Override
    public NodeVisitor child(String ns, String name) {
        AxmlNode node = new AxmlNode();
        node.name = name;
        node.ns = ns;
        firsts.add(node);
        return node;
    }

    @Override
    public void ns(String prefix, String uri, int ln) {
        Namespace ns = new Namespace();
        ns.prefix = prefix;
        ns.uri = uri;
        ns.ln = ln;
        nses.add(ns);
    }

    public static class AxmlNode extends NodeVisitor {
        public List<Attribute> attrs = new ArrayList<Attribute>();
        public List<AxmlNode> children = new ArrayList<AxmlNode>();
        public Integer ln;
        public String ns, name;
        public TextNode text;

        public void accept(NodeVisitor nodeVisitor) {
            NodeVisitor nodeVisitor2 = nodeVisitor.child(ns, name);
            acceptB(nodeVisitor2);
            nodeVisitor2.end();
        }

        public void acceptB(NodeVisitor nodeVisitor) {
            if (text != null) {
                text.accept(nodeVisitor);
            }
            for (Attribute a : attrs) {
                a.accept(nodeVisitor);
            }
            if (ln != null) {
                nodeVisitor.line(ln);
            }
            for (AxmlNode c : children) {
                c.accept(nodeVisitor);
            }
        }

        @Override
        public void attr(String ns, String name, int resourceId, int type, Object obj) {
            Attribute attr = new Attribute();
            attr.name = name;
            attr.ns = ns;
            attr.resourceId = resourceId;
            attr.type = type;
            attr.value = obj;
            attrs.add(attr);
        }

        @Override
        public NodeVisitor child(String ns, String name) {
            AxmlNode node = new AxmlNode();
            node.name = name;
            node.ns = ns;
            children.add(node);
            return node;
        }

        @Override
        public void line(int ln) {
            this.ln = ln;
        }

        @Override
        public void text(int lineNumber, String value) {
            TextNode text = new TextNode();
            text.ln = lineNumber;
            text.text = value;
            this.text = text;
        }

        public static class Attribute {
            public String ns, name;
            public int resourceId, type;
            public Object value;

            public void accept(NodeVisitor nodeVisitor) {
                nodeVisitor.attr(ns, name, resourceId, type, value);
            }
        }

        public static class TextNode {
            public int ln;
            public String text;

            public void accept(NodeVisitor nodeVisitor) {
                nodeVisitor.text(ln, text);
            }
        }
    }

    public static class Namespace {
        public int ln;
        public String prefix, uri;

        public void accept(Visitor visitor) {
            visitor.ns(prefix, uri, ln);
        }
    }
}
