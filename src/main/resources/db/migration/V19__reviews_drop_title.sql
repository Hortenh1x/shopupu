-- Reviews no longer have a title: the UI shows the author's name where the
-- title used to be. Contract step: drops the column and its data everywhere.
alter table reviews
    drop column if exists title;
