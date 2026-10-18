-- 10 categorii radacina
-- 70 subcategorii
-- 20 sub subcategorii

INSERT INTO category(name, parent_id)
SELECT 'Category ' || number, NULL
FROM generate_series(1,10) AS number;

INSERT INTO category (name, parent_id)
SELECT
    'Subcategory ' || root.id || '.' || number,
    root.id
FROM category AS root
         CROSS JOIN generate_series(1, 7) AS number --produs cartezian
WHERE root.parent_id IS NULL;

INSERT INTO category (name, parent_id)
SELECT
    'Third-level category ' || name,
    id
FROM category
WHERE name LIKE 'Subcategory %.1'
   OR name LIKE 'Subcategory %.2';