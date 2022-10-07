-- !Ups

create table "local_repo_config" ("org_id" INTEGER NOT NULL,"scan_id" INTEGER NOT NULL,"repo_name" VARCHAR NOT NULL,"repo_id" SERIAL NOT NULL,"local_path" VARCHAR NOT NULL,"remote" VARCHAR NOT NULL,"remote_type" VARCHAR NOT NULL,"branches" text [] NOT NULL);

alter table "local_repo_config" add constraint "local_repo_config_pk" primary key("org_id","local_path");

create index "local_repo_config_org_idx" on "local_repo_config" ("org_id");

create unique index "local_repo_config_repo_id_idx" on "local_repo_config" ("repo_id");

create table "local_scan_directory" ("org_id" INTEGER NOT NULL,"id" SERIAL NOT NULL PRIMARY KEY,"path" VARCHAR NOT NULL);

create table "organization" ("id" SERIAL NOT NULL PRIMARY KEY,"owner_id" INTEGER NOT NULL,"name" VARCHAR NOT NULL);

create table "local_org_settings" ("org_id" INTEGER NOT NULL PRIMARY KEY,"completed_nux" BOOLEAN NOT NULL);

create table "repo_settings" ("repo_id" INTEGER NOT NULL PRIMARY KEY,"intent" VARCHAR NOT NULL);

create table "repo_sha" ("repo_id" INTEGER NOT NULL,"sha" VARCHAR NOT NULL,"parents" text [] NOT NULL,"branches" text [] NOT NULL,"refs" text [] NOT NULL,"message" VARCHAR NOT NULL);

alter table "repo_sha" add constraint "repo_sha_pk" primary key("repo_id","sha");

create table "repo_sha_index" ("id" SERIAL NOT NULL PRIMARY KEY,"org_id" INTEGER NOT NULL,"repo_name" VARCHAR NOT NULL,"repo_id" INTEGER NOT NULL,"sha" VARCHAR NOT NULL,"root_sha_id" INTEGER,"dirty_signature" jsonb,"work_id" VARCHAR NOT NULL,"deleted" BOOLEAN NOT NULL,"created" BIGINT NOT NULL);

create index "repo_sha_index_repo_idx" on "repo_sha_index" ("repo_id");

create index "repo_sha_index_repo_sha_idx" on "repo_sha_index" ("repo_id","sha");

create table "repo_path_expansion" ("user_id" INTEGER NOT NULL,"repo" VARCHAR NOT NULL,"path" VARCHAR NOT NULL);

alter table "repo_path_expansion" add constraint "repo_path_expansion_pk" primary key("user_id","repo","path");

create table "analysis_tree" ("index_id" INTEGER NOT NULL,"file" VARCHAR NOT NULL,"analysis_type" VARCHAR NOT NULL);

alter table "analysis_tree" add constraint "analysis_tree_pk" primary key("index_id","file","analysis_type");

create index "analysis_tree_sha_idx" on "analysis_tree" ("index_id");

create table "sha_index_tree" ("index_id" INTEGER NOT NULL,"file" VARCHAR NOT NULL,"deleted" BOOLEAN NOT NULL);

alter table "sha_index_tree" add constraint "sha_index_tree_pk" primary key("index_id","file");

create index "sha_index_tree_sha_idx" on "sha_index_tree" ("index_id");

create table "repo_sha_compilation" ("sha_id" INTEGER NOT NULL PRIMARY KEY,"status" VARCHAR NOT NULL);

create table "saved_query" ("id" SERIAL NOT NULL PRIMARY KEY,"org_id" INTEGER NOT NULL,"name" VARCHAR NOT NULL,"language" VARCHAR NOT NULL,"nodes" jsonb NOT NULL,"edges" jsonb NOT NULL,"aliases" jsonb NOT NULL,"defaulted_selected" VARCHAR NOT NULL,"temporary" BOOLEAN NOT NULL,"created" BIGINT NOT NULL);

create table "saved_targeting" ("id" SERIAL NOT NULL PRIMARY KEY,"org_id" INTEGER NOT NULL,"name" VARCHAR NOT NULL,"repo" INTEGER,"branch" VARCHAR,"sha" VARCHAR,"file_filter" VARCHAR);

create table "schema" ("id" SERIAL NOT NULL PRIMARY KEY,"org_id" INTEGER NOT NULL,"title" VARCHAR NOT NULL,"field_aliases" jsonb NOT NULL,"saved_query_id" INTEGER NOT NULL,"selected" text [] NOT NULL,"sequence" text [] NOT NULL,"named" text [] NOT NULL,"file_filter" VARCHAR,"selected_repos" int4 [] NOT NULL);

create table "snapshot" ("schema_id" INTEGER NOT NULL,"index_id" INTEGER NOT NULL,"work_id" VARCHAR NOT NULL,"status" VARCHAR NOT NULL);

alter table "snapshot" add constraint "snapshot_pk" primary key("schema_id","index_id");

create table "annotation_column" ("id" SERIAL NOT NULL PRIMARY KEY,"schema_id" INTEGER NOT NULL,"name" VARCHAR NOT NULL,"column_type" VARCHAR NOT NULL);

create table "annotation" ("id" SERIAL NOT NULL PRIMARY KEY,"col_id" INTEGER NOT NULL,"index_id" INTEGER NOT NULL,"row_key" VARCHAR NOT NULL,"payload" jsonb NOT NULL);

create table "documentation" ("id" SERIAL NOT NULL PRIMARY KEY,"org_id" INTEGER NOT NULL,"parent_id" INTEGER);

create table "documentation_block" ("id" SERIAL NOT NULL PRIMARY KEY,"org_id" INTEGER NOT NULL,"parent_id" INTEGER NOT NULL,"text" VARCHAR NOT NULL);

create table "query_cache" ("id" SERIAL NOT NULL PRIMARY KEY,"org_id" INTEGER NOT NULL,"query_id" INTEGER NOT NULL,"force_root" VARCHAR NOT NULL,"ordering" text [] NOT NULL,"file_filter" VARCHAR);

create table "query_cache_key" ("cache_id" INTEGER NOT NULL,"key" VARCHAR NOT NULL,"count" INTEGER NOT NULL,"deleted" BOOLEAN NOT NULL,"last_modified" BIGINT NOT NULL);

alter table "query_cache_key" add constraint "query_cache_key_pk" primary key("cache_id","key");

create table "query_cache_cursor" ("cache_id" INTEGER NOT NULL,"key" VARCHAR NOT NULL,"start" INTEGER NOT NULL,"end" INTEGER NOT NULL,"cursor" jsonb NOT NULL);

alter table "query_cache_cursor" add constraint "query_cache_cursor_pk" primary key("cache_id","key","start");

-- !Downs

alter table "local_repo_config" drop constraint "local_repo_config_pk";

drop table "local_repo_config";

drop table "local_scan_directory";

drop table "organization";

drop table "local_org_settings";

drop table "repo_settings";

alter table "repo_sha" drop constraint "repo_sha_pk";

drop table "repo_sha";

drop table "repo_sha_index";

alter table "repo_path_expansion" drop constraint "repo_path_expansion_pk";

drop table "repo_path_expansion";

alter table "analysis_tree" drop constraint "analysis_tree_pk";

drop table "analysis_tree";

alter table "sha_index_tree" drop constraint "sha_index_tree_pk";

drop table "sha_index_tree";

drop table "repo_sha_compilation";

drop table "saved_query";

drop table "saved_targeting";

drop table "schema";

alter table "snapshot" drop constraint "snapshot_pk";

drop table "snapshot";

drop table "annotation_column";

drop table "annotation";

drop table "documentation";

drop table "documentation_block";

drop table "query_cache";

alter table "query_cache_key" drop constraint "query_cache_key_pk";

drop table "query_cache_key";

alter table "query_cache_cursor" drop constraint "query_cache_cursor_pk";

drop table "query_cache_cursor";
