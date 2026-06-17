create database if not exists ai_customer_service default character set utf8mb4 collate utf8mb4_unicode_ci;
use ai_customer_service;

create table if not exists knowledge_base (
    id bigint primary key,
    name varchar(100) not null,
    description varchar(500) null,
    status varchar(32) not null default 'ACTIVE',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0
) engine=InnoDB default charset=utf8mb4;

create table if not exists knowledge_document (
    id bigint primary key,
    knowledge_base_id bigint not null,
    file_name varchar(255) not null,
    file_type varchar(32) not null,
    file_path varchar(500) not null,
    content_hash varchar(128) not null,
    parse_status varchar(32) not null,
    error_message text null,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    index idx_kd_kb_id (knowledge_base_id),
    index idx_kd_hash (content_hash)
) engine=InnoDB default charset=utf8mb4;

create table if not exists knowledge_chunk (
    id bigint primary key,
    knowledge_base_id bigint not null,
    document_id bigint not null,
    chunk_index int not null,
    content mediumtext not null,
    token_count int not null default 0,
    metadata_json json null,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    index idx_kc_kb_id (knowledge_base_id),
    index idx_kc_doc_id (document_id)
) engine=InnoDB default charset=utf8mb4;

create table if not exists conversation (
    id bigint primary key,
    user_id bigint null,
    title varchar(100) not null,
    channel varchar(32) not null default 'WEB',
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    index idx_conversation_user_id (user_id)
) engine=InnoDB default charset=utf8mb4;

create table if not exists chat_message (
    id bigint primary key,
    conversation_id bigint not null,
    role varchar(32) not null,
    content mediumtext not null,
    rag_sources_json json null,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    index idx_msg_conversation_id (conversation_id)
) engine=InnoDB default charset=utf8mb4;
