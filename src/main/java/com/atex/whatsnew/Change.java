package com.atex.whatsnew;

public class Change implements Comparable<Change>
{
    public String id;
    public String date;
    public String change;
    public String preview;
    public String previewUrl;
    public String getId() { return id; }
    public String getDate() { return date; }
    public String getChange() { return change; }
    public String getPreview() { return preview; }

    public int compareTo(Change o) {
        if (date == null) {
            if (o.date != null) {
                return -1;
            }
            return o.id.compareTo(o.id);
        }
        if (o.date == null) {
            return 1;
        }
        return date.compareTo(o.date);
    }
}