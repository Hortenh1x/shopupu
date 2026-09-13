-- Additive: old images remain schema-compatible. Unknown provider outcomes retain their operation identity.
alter table payments add column refund_operation_key varchar(64);
alter table payments add column refund_status varchar(32);
alter table payments add column refund_external_id varchar(128);
create unique index uq_payments_refund_operation_key on payments (refund_operation_key);
create index idx_payments_refund_pending on payments (refund_status) where refund_status in ('PENDING', 'UNKNOWN');
