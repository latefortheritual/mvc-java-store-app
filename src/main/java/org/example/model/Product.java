package org.example.model;

import java.math.BigDecimal;

public class Product {
    private final int id;
    private final String name;
    private final int brand_id;
    private final int category_id;
    private final BigDecimal price;

    public Product(int id, String name, int brand_id, int category_id, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.brand_id = brand_id;
        this.category_id = category_id;
        this.price = price;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getBrand_id() {
        return brand_id;
    }

    public int getCategory_id() {
        return category_id;
    }

    public BigDecimal getPrice() {
        return price;
    }
}
