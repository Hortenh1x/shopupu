-- Only check-full-stack.sh may load this into the container ID it just created.
-- No real contact details or external integrations are used.
begin;
insert into users(email,password_hash,username,first_name,enabled,email_verified)
select prefix || '-' || :'nonce' || '@example.invalid', password_hash,
       prefix || '-' || :'nonce', case when prefix = 'buyer-a' then 'Account A' else 'Account B' end, true,true
from users cross join (values ('buyer-a'),('buyer-b'),('manager'),('admin-de')) names(prefix)
where email = :'admin_email';
insert into user_roles(user_id,role_id)
select u.id,r.id from users u cross join roles r
where u.email in ('buyer-a-' || :'nonce' || '@example.invalid', 'buyer-b-' || :'nonce' || '@example.invalid')
  and r.name='CUSTOMER';
insert into user_roles(user_id,role_id)
select u.id,r.id from users u cross join roles r
where u.email='manager-' || :'nonce' || '@example.invalid' and r.name in ('MANAGER','CUSTOMER');
-- Second, not-yet-enrolled ADMIN: the German locale acceptance enrolls MFA through the browser,
-- while the HTTP smoke already enrolled the bootstrap admin and manager via the API.
insert into user_roles(user_id,role_id)
select u.id,r.id from users u cross join roles r
where u.email='admin-de-' || :'nonce' || '@example.invalid' and r.name in ('ADMIN','CUSTOMER');
insert into categories(name,slug) values('Acceptance clothing','acceptance-' || :'nonce');
insert into products(title,slug,description,price,category_id,enabled)
select 'Acceptance Tee', 'acceptance-tee-' || :'nonce', 'A fictional clothing item for isolated verification.',19.00,id,true
from categories where slug='acceptance-' || :'nonce';
insert into product_variants(product_id,sku,size,color,price,enabled)
select id, 'ACCEPT-' || :'nonce','M','Blue',19.00,true from products where slug='acceptance-tee-' || :'nonce';
insert into inventory(variant_id,stock,reserved)
select id,100,0 from product_variants where sku='ACCEPT-' || :'nonce';
insert into reviews(user_id,product_id,rating,body,status,source)
select u.id,p.id,5,'Synthetic acceptance review. <img src=x onerror="window.__acceptanceInjection=1">','APPROVED','SYNTHETIC_DEMO'
from users u cross join products p where u.email='buyer-b-' || :'nonce' || '@example.invalid'
  and p.slug='acceptance-tee-' || :'nonce';
commit;
select json_build_object(
 'runNonce', :'nonce', 'adminEmail', :'admin_email',
 'buyerAEmail', 'buyer-a-' || :'nonce' || '@example.invalid',
 'buyerBEmail', 'buyer-b-' || :'nonce' || '@example.invalid',
 'managerEmail', 'manager-' || :'nonce' || '@example.invalid',
 'adminDeEmail', 'admin-de-' || :'nonce' || '@example.invalid',
 'productId', p.id, 'productSlug',p.slug,'variantId',v.id)
from products p join product_variants v on v.product_id=p.id where p.slug='acceptance-tee-' || :'nonce';
