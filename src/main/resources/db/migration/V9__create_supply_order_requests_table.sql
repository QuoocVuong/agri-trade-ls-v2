-- V9__create_supply_order_requests_table.sql
CREATE TABLE supply_order_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    farmer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    requested_quantity INT NOT NULL,
    requested_unit VARCHAR(50) NOT NULL,
    proposed_price_per_unit DECIMAL(15, 2) NULL,
    buyer_notes TEXT NULL,
    shipping_full_name VARCHAR(100) NULL,
    shipping_phone_number VARCHAR(20) NULL,
    shipping_address_detail VARCHAR(255) NULL,
    shipping_province_code VARCHAR(10) NULL,
    shipping_district_code VARCHAR(10) NULL,
    shipping_ward_code VARCHAR(10) NULL,
    expected_delivery_date DATE NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_FARMER_ACTION',
    farmer_response_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (buyer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_sor_buyer_id ON supply_order_requests(buyer_id);
CREATE INDEX idx_sor_farmer_id ON supply_order_requests(farmer_id);
CREATE INDEX idx_sor_product_id ON supply_order_requests(product_id);
CREATE INDEX idx_sor_status ON supply_order_requests(status);