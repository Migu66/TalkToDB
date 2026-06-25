-- Esquema de prueba para los tests de integración E2E (H2 en memoria).
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS cities;

CREATE TABLE cities (
    id   INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE users (
    id      INT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    city    VARCHAR(100),
    city_id INT,
    CONSTRAINT fk_user_city FOREIGN KEY (city_id) REFERENCES cities(id)
);
