-- Video schema. Run by the schema owner (videoadmin); the runtime user (theuser) receives data
-- access to these tables through the default privileges set when the database was created.

create table video (
    id          uuid         primary key,
    title       varchar(30)  not null,
    description varchar(100),
    -- The platform user who owns the video (the user ID from user-service); null for the starter set.
    owner_id    uuid,
    completed   boolean      not null default false,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    version     bigint       not null default 0,
    constraint uq_video_title unique (title)
);

create index idx_video_owner on video (owner_id);
