package com.rescuereach.data.model;

public class SafetyTip {
    private String title;
    private String content;
    private int iconResId;

    public SafetyTip(String title, String content, int iconResId) {
        this.title = title;
        this.content = content;
        this.iconResId = iconResId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getIconResId() {
        return iconResId;
    }

    public void setIconResId(int iconResId) {
        this.iconResId = iconResId;
    }
}