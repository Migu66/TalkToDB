-- Datos de prueba para los tests de integración E2E.
INSERT INTO cities (id, name) VALUES (1, 'Madrid'), (2, 'Barcelona');

INSERT INTO users (id, name, city, city_id) VALUES
    (1, 'Ana',   'Madrid',    1),
    (2, 'Luis',  'Barcelona', 2),
    (3, 'Lucía', 'Madrid',    1);
