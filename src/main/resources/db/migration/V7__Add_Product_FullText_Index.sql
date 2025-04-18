-- V7__Add_Product_FullText_Index.sql

-- Thêm FULLTEXT index cho cột name và description của bảng products
-- Lưu ý: FULLTEXT index chỉ hỗ trợ trên MyISAM hoặc InnoDB (từ MySQL 5.6+)
ALTER TABLE products ADD FULLTEXT INDEX idx_ft_product_name_desc (name, description);