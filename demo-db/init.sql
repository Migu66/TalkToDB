-- Datos de demostración para probar la app SQL IA Translator (base de datos H2).
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS cities;

CREATE TABLE cities (
    id   INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE users (
    id      INT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    email   VARCHAR(150),
    age     INT,
    city    VARCHAR(100),
    city_id INT,
    CONSTRAINT fk_user_city FOREIGN KEY (city_id) REFERENCES cities(id)
);

INSERT INTO cities (id, name) VALUES
    (1, 'Madrid'),
    (2, 'Barcelona'),
    (3, 'Valencia'),
    (4, 'Sevilla');

INSERT INTO users (id, name, email, age, city, city_id) VALUES
    (1, 'Ana García',     'ana@example.com',   34, 'Madrid',    1),
    (2, 'Luis Pérez',     'luis@example.com',   28, 'Barcelona', 2),
    (3, 'Lucía Fernández','lucia@example.com',  41, 'Madrid',    1),
    (4, 'Marcos Ruiz',    'marcos@example.com', 23, 'Valencia',  3),
    (5, 'Sara Molina',    'sara@example.com',   37, 'Sevilla',   4),
    (6, 'Diego Torres',   'diego@example.com',  52, 'Madrid',    1),
    (7, 'Elena Navarro',  'elena@example.com',  29, 'Barcelona', 2);
