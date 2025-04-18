-- V6__Enhance_Product_Image_And_Approval.sql

-- Thêm cột thứ tự hiển thị cho ảnh sản phẩm
ALTER TABLE product_images
ADD COLUMN display_order INT NOT NULL DEFAULT 0 COMMENT 'Thứ tự hiển thị ảnh (số nhỏ hơn hiển thị trước)';

-- Thêm index cho thứ tự hiển thị để sắp xếp nhanh
CREATE INDEX idx_product_images_order ON product_images(product_id, display_order);

-- Thêm cột lý do từ chối cho sản phẩm
ALTER TABLE products
ADD COLUMN reject_reason TEXT NULL COMMENT 'Lý do Admin từ chối sản phẩm';