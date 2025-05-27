-- V3__create_ordering_tables.sql

-- Bảng Cart Items (Giỏ hàng)
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_user_product_cart (user_id, product_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_cart_items_user_id ON cart_items(user_id);

-- Bảng Orders (Đơn hàng)
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(20) UNIQUE NOT NULL,
    buyer_id BIGINT NOT NULL,
    farmer_id BIGINT NOT NULL,
    order_type ENUM('B2C', 'B2B') NOT NULL,
    shipping_full_name VARCHAR(100) NOT NULL,
    shipping_phone_number VARCHAR(20) NOT NULL,
    shipping_address_detail VARCHAR(255) NOT NULL,
    shipping_province_code VARCHAR(10) NOT NULL,
    shipping_district_code VARCHAR(10) NOT NULL,
    shipping_ward_code VARCHAR(10) NOT NULL,
    sub_total DECIMAL(15, 2) NOT NULL,
    shipping_fee DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(15, 2) NOT NULL,
    payment_method ENUM('COD', 'BANK_TRANSFER', 'VNPAY', 'MOMO', 'ZALOPAY', 'INVOICE', 'OTHER') NOT NULL DEFAULT 'COD',
    payment_status ENUM('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'AWAITING_PAYMENT_TERM') NOT NULL DEFAULT 'PENDING',
    status ENUM('PENDING', 'AWAITING_PAYMENT', 'CONFIRMED', 'PROCESSING', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURNED') NOT NULL DEFAULT 'PENDING',
    notes TEXT NULL,
    purchase_order_number VARCHAR(50) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE RESTRICT, -- Không cho xóa user nếu có đơn hàng
    FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX idx_orders_order_code ON orders(order_code);
CREATE INDEX idx_orders_buyer_id ON orders(buyer_id);
CREATE INDEX idx_orders_farmer_id ON orders(farmer_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);

-- Bảng Order Items (Chi tiết sản phẩm trong đơn hàng)
CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    price_per_unit DECIMAL(15, 2) NOT NULL,
    quantity INT NOT NULL,
    total_price DECIMAL(15, 2) NOT NULL,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- Bảng Payments (Lịch sử giao dịch thanh toán)
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    transaction_code VARCHAR(100) UNIQUE NULL,
    payment_gateway VARCHAR(50) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status ENUM('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED') NOT NULL DEFAULT 'PENDING',
    payment_time TIMESTAMP NULL,
    gateway_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_transaction_code ON payments(transaction_code);
CREATE INDEX idx_payments_status ON payments(status);

-- Bảng Invoices (Hóa đơn, đặc biệt cho B2B hoặc khi cần xuất hóa đơn)
CREATE TABLE invoices (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE,
    invoice_number VARCHAR(30) UNIQUE NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NULL,
    total_amount DECIMAL(15, 2) NOT NULL,
    status ENUM('DRAFT', 'ISSUED', 'PAID', 'VOID', 'OVERDUE') NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
CREATE INDEX idx_invoices_order_id ON invoices(order_id);
CREATE INDEX idx_invoices_status_due_date ON invoices(status, due_date);