package com.nirithy.AxmlEdit.core;

public class AxmlStringItem {
    public String data;
    public int dataOffset;
    public int index;

    public AxmlStringItem() {
        super();
    }

    public AxmlStringItem(String data) {
        super();
        this.data = data;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        AxmlStringItem other = (AxmlStringItem) obj;
        if (data == null) {
            return other.data == null;
        } else return data.equals(other.data);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((data == null) ? 0 : data.hashCode());
        return result;
    }

    public String toString() {
        return String.format("S%04d %s", index, data);
    }

}
