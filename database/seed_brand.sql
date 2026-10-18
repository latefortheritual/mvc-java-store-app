INSERT INTO brand(name)
SELECT 'Brand ' || number
FROM generate_series(1,100) AS number;