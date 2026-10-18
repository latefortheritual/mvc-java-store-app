package org.example.model;

public class Category {
    private final int id;
    private final Integer parent_id;
    private final String name;

    public Category(int id, Integer parent_id, String name) {
        this.id = id;
        this.parent_id = parent_id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public Integer getParent_id() {
        return parent_id;
    }

    public String getName() {
        return name;
    }
}
