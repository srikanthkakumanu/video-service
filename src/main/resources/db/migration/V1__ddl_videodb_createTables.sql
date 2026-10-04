CREATE TABLE tbl_video (
    id uuid PRIMARY KEY,
    title varchar(30) NOT NULL UNIQUE,
    description varchar(100),
    user_id uuid,
    user_name varchar(255),
    completed boolean NOT NULL DEFAULT false,
    created timestamptz,
    updated timestamptz
);
CREATE INDEX idx_tbl_video_user_id ON tbl_video(user_id);
