CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE facilities (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(200),
    capacity INTEGER
);

INSERT INTO users (username, email) VALUES
    ('admin', 'admin@srelab.ai'),
    ('operator', 'operator@srelab.ai'),
    ('viewer', 'viewer@srelab.ai');

INSERT INTO facilities (name, location, capacity) VALUES
    ('Data Center Alpha', 'US-East-1', 1000),
    ('Data Center Beta', 'EU-West-1', 500);
