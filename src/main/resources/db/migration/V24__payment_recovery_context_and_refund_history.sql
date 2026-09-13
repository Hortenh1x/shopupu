-- Additive recovery metadata: no secrets are stored in provider_request_context.
alter table payments add column provider_request_context text;
create index idx_payments_create_recovery on payments (provider, id)
    where status = 'CREATED' and external_id is null;

create table payment_refund_attempts (
    operation_key varchar(64) primary key,
    payment_id bigint not null references payments (id) on delete cascade,
    provider varchar(32) not null,
    status varchar(32) not null,
    external_refund_id varchar(128),
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now()
);
create index idx_refund_attempts_payment on payment_refund_attempts (payment_id);

-- Preserve the current operation for deployments that already contain V21 refund data.
insert into payment_refund_attempts (operation_key, payment_id, provider, status, external_refund_id, created_at, updated_at)
select refund_operation_key, id, provider, coalesce(refund_status, 'UNKNOWN'), refund_external_id,
       coalesce(updated_at, created_at, now()), coalesce(updated_at, created_at, now())
from payments where refund_operation_key is not null;
