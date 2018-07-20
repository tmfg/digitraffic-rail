CREATE TABLE `third_category_code` (
  `code` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `description` varchar(4000) NULL,
  `id` bigint(20) NOT NULL,
  `valid_from` date DEFAULT NULL,
  `valid_to` date DEFAULT NULL,
  `detailed_category_code_id` BIGINT(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `third_category_code_idx_u` (`id`)
);

alter table cause add column third_category_code_id bigint(20) null;