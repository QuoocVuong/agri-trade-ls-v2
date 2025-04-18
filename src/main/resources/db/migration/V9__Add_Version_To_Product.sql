-- V9__Add_Version_To_Product.sql
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;