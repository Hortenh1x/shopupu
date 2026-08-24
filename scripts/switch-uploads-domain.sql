-- Product image URLs are baked into product_images.url at upload time
-- (LocalFileStorageService), so moving to the public domain needs a one-time
-- rewrite. New uploads get the right base from PUBLIC_UPLOADS_BASE_URL.
--
-- Apply:    docker compose exec -T db psql -U shopupu -d shopupu < scripts/switch-uploads-domain.sql
-- Rollback: swap the two URLs.
update product_images
set url = replace(url, 'http://localhost:8080/uploads', 'https://shopupu.net/uploads')
where url like 'http://localhost:8080/uploads%';
