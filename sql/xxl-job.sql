CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE xxl_job;

CREATE TABLE IF NOT EXISTS xxl_job_group (
  id int(11) NOT NULL AUTO_INCREMENT,
  app_name varchar(64) NOT NULL,
  title varchar(12) NOT NULL,
  address_type tinyint(4) NOT NULL DEFAULT '0',
  address_list text,
  update_time datetime DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS xxl_job_info (
  id int(11) NOT NULL AUTO_INCREMENT,
  job_group int(11) NOT NULL,
  job_desc varchar(255) NOT NULL,
  author varchar(64) DEFAULT NULL,
  schedule_type varchar(50) NOT NULL DEFAULT 'NONE',
  schedule_conf varchar(128) DEFAULT NULL,
  misfire_strategy varchar(50) NOT NULL DEFAULT 'DO_NOTHING',
  executor_route_strategy varchar(50) DEFAULT NULL,
  executor_handler varchar(255) DEFAULT NULL,
  executor_param varchar(512) DEFAULT NULL,
  executor_block_strategy varchar(50) DEFAULT NULL,
  executor_timeout int(11) NOT NULL DEFAULT '0',
  executor_fail_retry_count int(11) NOT NULL DEFAULT '0',
  glue_type varchar(50) NOT NULL,
  glue_source mediumtext,
  glue_remark varchar(128) DEFAULT NULL,
  glue_updatetime datetime DEFAULT NULL,
  child_jobid varchar(255) DEFAULT NULL,
  trigger_status tinyint(4) NOT NULL DEFAULT '0',
  trigger_last_time bigint(13) NOT NULL DEFAULT '0',
  trigger_next_time bigint(13) NOT NULL DEFAULT '0',
  update_time datetime DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS xxl_job_lock (
  lock_name varchar(50) NOT NULL,
  PRIMARY KEY (lock_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO xxl_job_lock(lock_name) VALUES ('schedule_lock');