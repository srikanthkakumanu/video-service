create table tbl_video (
    id varbinary(16) not null primary key,
    title varchar(30) unique,
    description varchar(100),
    user_id varbinary(16),
    user_name varchar(20),
    completed boolean default 0,
    created timestamp,
    updated timestamp
) engine=InnoDB;
