CREATE TABLE brand (
                       id SERIAL PRIMARY KEY,
                       name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE category (
                          id SERIAL PRIMARY KEY,
                          parent_id INTEGER REFERENCES category(id) ON DELETE CASCADE,
                          name VARCHAR(255) NOT NULL
);

CREATE TABLE product (
                         id SERIAL PRIMARY KEY,
                         name VARCHAR(255) NOT NULL,
                         brand_id INTEGER NOT NULL REFERENCES brand(id) ON DELETE CASCADE,
                         category_id INTEGER NOT NULL REFERENCES category(id) ON DELETE CASCADE,
                         price NUMERIC(10,2) NOT NULL CHECK (price >= 0)
);

CREATE INDEX idx_category_parent_id
    ON category (parent_id);

CREATE INDEX idx_product_brand_id
    ON product(brand_id);

CREATE INDEX idx_product_category_id
    ON product (category_id);