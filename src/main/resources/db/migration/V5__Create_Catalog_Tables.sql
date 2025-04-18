-- V5__Create_Catalog_Tables.sql

-- Bảng Categories (Danh mục sản phẩm)
CREATE TABLE categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) UNIQUE NOT NULL COMMENT 'Slug cho URL thân thiện',
    description TEXT NULL,
    image_url VARCHAR(512) NULL,
    parent_id INT NULL COMMENT 'Danh mục cha (cho cấu trúc đa cấp)',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL ON UPDATE CASCADE -- Hoặc CASCADE nếu muốn xóa con khi xóa cha
);
CREATE UNIQUE INDEX idx_categories_slug ON categories(slug);
CREATE INDEX idx_categories_parent_id ON categories(parent_id);

-- Bảng Products (Nông sản)
CREATE TABLE products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    farmer_id BIGINT NOT NULL COMMENT 'FK users.id (Người bán)',
    category_id INT NOT NULL COMMENT 'FK categories.id',
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(280) UNIQUE NOT NULL,
    description LONGTEXT NULL, -- Dùng LONGTEXT cho mô tả dài
    unit VARCHAR(50) NOT NULL COMMENT 'Đơn vị B2C (kg, bó, quả)',
    price DECIMAL(15, 2) NOT NULL COMMENT 'Giá B2C',
    stock_quantity INT NOT NULL DEFAULT 0,
    status ENUM('DRAFT', 'PENDING_APPROVAL', 'PUBLISHED', 'UNPUBLISHED', 'REJECTED') NOT NULL DEFAULT 'DRAFT',
    province_code VARCHAR(10) NOT NULL COMMENT 'Mã tỉnh (lấy từ nông dân)',
    average_rating FLOAT NOT NULL DEFAULT 0.0,
    rating_count INT NOT NULL DEFAULT 0,
    is_b2b_available BOOLEAN NOT NULL DEFAULT FALSE,
    b2b_unit VARCHAR(50) NULL COMMENT 'Đơn vị B2B (thùng, tạ)',
    min_b2b_quantity INT NULL DEFAULT 1,
    b2b_base_price DECIMAL(15, 2) NULL COMMENT 'Giá B2B cơ bản',
    favorite_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (farmer_id) REFERENCES users(id) ON DELETE CASCADE, -- Xóa sản phẩm nếu nông dân bị xóa
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT -- Không cho xóa danh mục nếu còn sản phẩm
);
-- Thêm index cho các cột thường dùng để lọc/tìm kiếm
CREATE UNIQUE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_farmer_id ON products(farmer_id);
CREATE INDEX idx_products_category_id ON products(category_id);
CREATE INDEX idx_products_status ON products(status);
CREATE INDEX idx_products_province_code ON products(province_code);
CREATE INDEX idx_products_is_deleted ON products(is_deleted);
-- Cân nhắc thêm FULLTEXT index cho name, description nếu cần tìm kiếm text hiệu quả
-- CREATE FULLTEXT INDEX idx_products_name_desc ON products(name, description);

-- Bảng Product Images (Ảnh sản phẩm)
CREATE TABLE product_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE -- Xóa ảnh nếu sản phẩm bị xóa
);
CREATE INDEX idx_product_images_product_id ON product_images(product_id);

-- Bảng Product Pricing Tiers (Giá B2B theo bậc - Tùy chọn)
CREATE TABLE product_pricing_tiers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    min_quantity INT NOT NULL COMMENT 'Số lượng tối thiểu (theo b2b_unit)',
    price_per_unit DECIMAL(15, 2) NOT NULL COMMENT 'Giá cho mỗi b2b_unit',
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
);
CREATE INDEX idx_product_pricing_tiers_product_id ON product_pricing_tiers(product_id);

-- Cập nhật bảng users để thêm mối quan hệ ngược lại (nếu cần)
-- ALTER TABLE products ADD CONSTRAINT fk_product_farmer FOREIGN KEY (farmer_id) REFERENCES users(id);
-- ALTER TABLE products ADD CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(id);
-- (Các FK đã được thêm trong CREATE TABLE)