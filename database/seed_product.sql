INSERT INTO product (name, brand_id, category_id, price)
SELECT
    'Product ' || number,
    ((number - 1) % 100) + 1,
    ((number - 1) % 100) + 1,
    ROUND((1 + random() * 999)::NUMERIC, 2)
FROM generate_series(1, 1000000) AS number;