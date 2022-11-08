-- !Ups

create table "local_repo_config" ("org_id" INTEGER NOT NULL,"repo_name" VARCHAR NOT NULL,"repo_id" SERIAL NOT NULL,"local_path" VARCHAR NOT NULL,"remote" VARCHAR NOT NULL,"remote_type" VARCHAR NOT NULL,"branches" text [] NOT NULL);

alter table "local_repo_config" add constraint "local_repo_config_pk" primary key("org_id","local_path");

create index "local_repo_config_org_idx" on "local_repo_config" ("org_id");

create unique index "local_repo_config_repo_id_idx" on "local_repo_config" ("repo_id");

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

-- !Downs

drop table "local_repo_config";

drop table "organization";

drop table "local_org_settings";

drop table "repo_settings";

drop table "repo_sha";

drop table "repo_sha_index";

drop table "repo_path_expansion";

drop table "analysis_tree";

drop table "sha_index_tree";

drop table "repo_sha_compilation";
