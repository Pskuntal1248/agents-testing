CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    sku VARCHAR(50) NOT NULL,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    price DOUBLE PRECISION NOT NULL
);

CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE order_items (
    id SERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(id),
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price DOUBLE PRECISION NOT NULL
);

INSERT INTO products (name, sku, stock_quantity, price) VALUES
    ('Server Rack Unit', 'SRV-RACK-001', 40, 1200.00),
    ('Network Switch 24-Port', 'NET-SW-024', 75, 450.00),
    ('Fiber Patch Cable 10m', 'CBL-FBR-10M', 500, 8.50),
    ('UPS Battery Backup', 'PWR-UPS-500', 60, 320.00),
    ('Cooling Fan Module', 'CLG-FAN-002', 120, 45.00);

INSERT INTO orders (customer_name, status) VALUES
    ('Acme Data Systems', 'COMPLETED'),
    ('Northwind Cloud', 'PENDING'),
    ('Globex Infrastructure', 'COMPLETED'),
    ('Initech Networks', 'SHIPPED'),
    ('Umbrella Hosting', 'PENDING');

INSERT INTO order_items (order_id, product_id, quantity, unit_price) VALUES
    (1, 1, 2, 1200.00), (1, 2, 4, 450.00), (1, 3, 20, 8.50),
    (2, 4, 3, 320.00), (2, 5, 10, 45.00),
    (3, 1, 1, 1200.00), (3, 4, 2, 320.00),
    (4, 2, 6, 450.00), (4, 3, 50, 8.50), (4, 5, 8, 45.00),
    (5, 1, 4, 1200.00), (5, 2, 2, 450.00);
