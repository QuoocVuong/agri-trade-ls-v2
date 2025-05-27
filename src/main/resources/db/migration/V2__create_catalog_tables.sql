-- V2__create_catalog_tables.sql

-- Bảng Categories (Danh mục sản phẩm)
CREATE TABLE categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) UNIQUE NOT NULL,
    description TEXT NULL,
    blob_path VARCHAR(1024) NULL COMMENT 'Đường dẫn tới file ảnh trên storage',
    parent_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL ON UPDATE CASCADE
);
CREATE UNIQUE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- Bảng Products (Nông sản)
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    farmer_id BIGINT NOT NULL,
    category_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(280) UNIQUE NOT NULL,
    description LONGTEXT NULL,
    unit VARCHAR(50) NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    status ENUM('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'UNPUBLISHED', 'REJECTED') NOT NULL DEFAULT 'DRAFT',
    province_code VARCHAR(10) NOT NULL,
    average_rating FLOAT NOT NULL DEFAULT 0.0,
    rating_count INT NOT NULL DEFAULT 0,
    b2b_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    b2b_unit VARCHAR(50) NULL,
    min_b2b_quantity INT NULL DEFAULT 1,
    b2b_base_price DECIMAL(15, 2) NULL,
    favorite_count INT NOT NULL DEFAULT 0,
    reject_reason TEXT NULL,
    weight_in_grams INT NULL COMMENT 'Trọng lượng của 1 unit (B2C) tính bằng gram',
    version BIGINT NOT NULL DEFAULT 0 COMMENT 'For optimistic locking',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_farmer_id ON products(farmer_id);
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_province_code ON products(province_code);
CREATE INDEX idx_products_is_deleted ON products(is_deleted);
ALTER TABLE products ADD FULLTEXT INDEX idx_ft_product_name_desc (name, description);

-- Bảng Product Images (Ảnh sản phẩm)
CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    blob_path VARCHAR(1024) NOT NULL COMMENT 'Đường dẫn tới file ảnh trên storage',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_product_images_product_id_order ON product_images(product_id, display_order);

-- Bảng Product Pricing Tiers (Giá B2B theo bậc)
CREATE TABLE product_pricing_tiers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    min_quantity INT NOT NULL,
    price_per_unit DECIMAL(15, 2) NOT NULL,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_product_pricing_tiers_product_id ON product_pricing_tiers(product_id);