-- V8__Create_Ordering_Tables.sql

-- Bảng Cart Items (Giỏ hàng - Lưu trong DB để giữ lại giữa các phiên)
CREATE TABLE cart_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT 'FK users.id (Người sở hữu giỏ hàng)',
    product_id BIGINT NOT NULL COMMENT 'FK products.id',
    quantity INT NOT NULL DEFAULT 1,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_user_product_cart (user_id, product_id) COMMENT 'Mỗi user chỉ có 1 dòng cho 1 product',
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE -- Xóa khỏi giỏ nếu SP bị xóa
);
CREATE INDEX idx_cart_items_user_id ON cart_items(user_id);

-- Bảng Orders (Đơn hàng)
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_code VARCHAR(20) UNIQUE NOT NULL COMMENT 'Mã đơn hàng duy nhất, thân thiện',
    buyer_id BIGINT NOT NULL COMMENT 'FK users.id (Người mua)',
    farmer_id BIGINT NOT NULL COMMENT 'FK users.id (Người bán)',
    order_type ENUM('B2C', 'B2B') NOT NULL,
    -- Thông tin giao hàng (sao chép)
    shipping_full_name VARCHAR(100) NOT NULL,
    shipping_phone_number VARCHAR(20) NOT NULL,
    shipping_address_detail VARCHAR(255) NOT NULL,
    shipping_province_code VARCHAR(10) NOT NULL,
    shipping_district_code VARCHAR(10) NOT NULL,
    shipping_ward_code VARCHAR(10) NOT NULL,
    -- Giá trị đơn hàng
    sub_total DECIMAL(15, 2) NOT NULL COMMENT 'Tổng tiền hàng',
    shipping_fee DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(15, 2) NOT NULL COMMENT 'Tổng tiền cuối cùng',
    -- Thanh toán & Trạng thái
    payment_method ENUM('COD', 'BANK_TRANSFER', 'VNPAY', 'MOMO', 'ZALOPAY', 'INVOICE', 'OTHER') NOT NULL DEFAULT 'COD',
    payment_status ENUM('PENDING', 'PAID', 'FAILED', 'REFUNDED', 'AWAITING_PAYMENT_TERM') NOT NULL DEFAULT 'PENDING',
    status ENUM('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPING', 'DELIVERED', 'CANCELLED', 'RETURNED') NOT NULL DEFAULT 'PENDING',
    notes TEXT NULL COMMENT 'Ghi chú của khách hàng',
    purchase_order_number VARCHAR(50) NULL COMMENT 'Số PO từ khách hàng B2B',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    product_id BIGINT NOT NULL COMMENT 'FK products.id',
    -- Thông tin sản phẩm tại thời điểm mua (sao chép)
    product_name VARCHAR(255) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    price_per_unit DECIMAL(15, 2) NOT NULL COMMENT 'Giá mỗi đơn vị tại thời điểm mua',
    quantity INT NOT NULL,
    total_price DECIMAL(15, 2) NOT NULL COMMENT 'quantity * price_per_unit',
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE, -- Xóa item nếu order bị xóa
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT -- Không cho xóa SP nếu đang có trong đơn hàng? Hoặc SET NULL
);
CREATE INDEX idx_order_items_order_id ON order_items(order_id);
CREATE INDEX idx_order_items_product_id ON order_items(product_id);

-- Bảng Payments (Lịch sử giao dịch thanh toán)
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    transaction_code VARCHAR(100) UNIQUE NULL COMMENT 'Mã giao dịch từ cổng thanh toán',
    payment_gateway VARCHAR(50) NOT NULL COMMENT 'COD, VNPAY, MOMO...',
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