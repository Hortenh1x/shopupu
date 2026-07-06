-- MANAGER role (AUTHZ-01)
insert into roles (name)
values ('MANAGER')
on conflict (name) do nothing;

-- Review moderation workflow (REV-02): PENDING -> APPROVED/REJECTED (+ DELETED)
alter table reviews
    drop constraint ck_reviews_status;

update reviews set status = 'APPROVED' where status = 'PUBLISHED';
update reviews set status = 'REJECTED' where status = 'HIDDEN';

alter table reviews
    add constraint ck_reviews_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'DELETED'));
