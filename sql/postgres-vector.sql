create database ai_customer_vector;

-- Run the following statements after connecting to database ai_customer_vector.
create extension if not exists vector;

create table if not exists rag_chunk_vector (
    chunk_id bigint primary key,
    knowledge_base_id bigint not null,
    document_id bigint not null,
    document_name varchar(255) not null,
    content text not null,
    embedding vector(768) not null,
    created_at timestamptz not null default now()
);

create index if not exists idx_rag_chunk_vector_kb_id
    on rag_chunk_vector (knowledge_base_id);

create index if not exists idx_rag_chunk_vector_document_id
    on rag_chunk_vector (document_id);

create index if not exists idx_rag_chunk_vector_embedding
    on rag_chunk_vector using hnsw (embedding vector_cosine_ops);
