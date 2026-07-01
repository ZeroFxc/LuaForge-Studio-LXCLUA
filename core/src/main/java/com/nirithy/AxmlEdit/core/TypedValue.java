package com.nirithy.AxmlEdit.core;

public class TypedValue {

    public static final int ID = 1;
    public static final int STYLE = 2;
    public static final int CLASS = 3;
    public final int type;
    public final String raw;
    public final int ref;

    private TypedValue(int type, int ref, String raw) {
        super();
        this.type = type;
        this.raw = raw;
        this.ref = ref;
    }

    public static TypedValue wrapId(int ref, String raw) {
        return new TypedValue(ID, ref, raw);
    }

    public static TypedValue wrapStyle(int ref, String raw) {
        return new TypedValue(STYLE, ref, raw);
    }

    public static TypedValue wrapClass(int ref, String raw) {
        return new TypedValue(CLASS, ref, raw);
    }

    public TypedValue replaceRaw(String raw) {
        return new TypedValue(type, ref, raw);
    }
}
