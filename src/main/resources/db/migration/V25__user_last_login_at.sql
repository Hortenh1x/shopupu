-- Retention (LEG-06): the published privacy page removes accounts without a sign-in for
-- 12 months. Track the last sign-in / session renewal; existing live accounts start from
-- their latest refresh token or, failing that, from this migration, so nobody is erased
-- before 12 months of measured inactivity.
alter table users add column last_login_at timestamp with time zone;

update users u
set last_login_at = coalesce((select max(r.created_at) from refresh_tokens r where r.user_id = u.id), now())
where u.deleted_at is null;

create index idx_users_last_login_at on users (last_login_at) where deleted_at is null;
create index idx_users_deleted_at on users (deleted_at) where deleted_at is not null;
