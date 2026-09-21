
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ad_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `duration_seconds` int DEFAULT NULL,
  `media_type` enum('AUDIO','DOCUMENT','IMAGE','MODEL','VIDEO') NOT NULL,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `ad_id` bigint DEFAULT NULL,
  `min_price_per_like_cents` bigint DEFAULT NULL,
  `version` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2b8xw83dg69wf2ld7lm3c5rtc` (`object_key`),
  KEY `FKmikaunjclkvqktpdi62pt6vhn` (`ad_id`),
  CONSTRAINT `FKmikaunjclkvqktpdi62pt6vhn` FOREIGN KEY (`ad_id`) REFERENCES `ads` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ad_likes` (
  `created_at` datetime(6) NOT NULL,
  `reward_amount` bigint NOT NULL,
  `consumer_user_id` bigint NOT NULL,
  `ad_id` bigint NOT NULL,
  PRIMARY KEY (`ad_id`,`consumer_user_id`),
  KEY `idx_ad_likes_created_at` (`created_at`),
  KEY `idx_ad_likes_consumer_id` (`consumer_user_id`),
  KEY `idx_ad_likes_ad_id` (`ad_id`),
  CONSTRAINT `FK7dqeq50befw985ymhhbgrnq97` FOREIGN KEY (`consumer_user_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FK9chysqyjyjftos6sjb734yho4` FOREIGN KEY (`ad_id`) REFERENCES `ads` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ad_watch_session` (
  `id` binary(16) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `resume_count` int DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','EXPIRED','INVALIDATED','LIKED','WATCHED') DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `ad_id` bigint DEFAULT NULL,
  `consumer_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_watch_session_user_ad_expires` (`consumer_user_id`,`ad_id`,`expires_at`),
  KEY `idx_watch_session_ad` (`ad_id`),
  CONSTRAINT `FKcxcfv4mnspsf7p439t49hfoyp` FOREIGN KEY (`ad_id`) REFERENCES `ads` (`id`),
  CONSTRAINT `FKtlo1wp9tjse32vhecmew77138` FOREIGN KEY (`consumer_user_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_details` (
  `admin_code` varchar(255) DEFAULT NULL,
  `last_pqrs_assigned_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `FKawedv5j0fpju53kxirfnlq8dv` FOREIGN KEY (`user_id`) REFERENCES `user_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `admin_reports` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_type` enum('ACCOUNT_FREEZE','ACCOUNT_UNFREEZE','BALANCE_ADJUSTMENT','BALANCE_BLOCK','BALANCE_UNBLOCK','BONUS_GRANT','PENALTY_APPLICATION') NOT NULL,
  `amount` decimal(19,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `new_balance` decimal(19,2) DEFAULT NULL,
  `new_blocked_balance` decimal(19,2) DEFAULT NULL,
  `previous_balance` decimal(19,2) DEFAULT NULL,
  `previous_blocked_balance` decimal(19,2) DEFAULT NULL,
  `reason` text,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ads` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `current_likes` int NOT NULL,
  `description` text NOT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `max_likes` int NOT NULL,
  `max_likes_per_user_per_day` int DEFAULT NULL,
  `rejection_reason` text,
  `reward_per_like` bigint NOT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','APPROVED','BLOCKED','COMPLETED','PAUSED','PENDING','REJECTED') NOT NULL,
  `target_url` varchar(500) DEFAULT NULL,
  `title` varchar(100) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `commercial_id` bigint NOT NULL,
  `target_audience_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ads_availability` (`status`,`current_likes`,`max_likes`,`end_date`,`created_at`),
  KEY `idx_ads_start_date` (`start_date`),
  KEY `idx_ads_commercial` (`commercial_id`),
  KEY `idx_ads_active_created` (`status`,`created_at`),
  KEY `idx_ads_completed` (`status`,`updated_at`),
  KEY `FK70nuuep731kqihx22m7t5mmsc` (`target_audience_id`),
  CONSTRAINT `FK70nuuep731kqihx22m7t5mmsc` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`),
  CONSTRAINT `fk_ad_commercial` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `ads_chk_1` CHECK (((`max_likes` <= 10000000) and (`max_likes` >= 1))),
  CONSTRAINT `ads_chk_2` CHECK (((`max_likes_per_user_per_day` >= 1) and (`max_likes_per_user_per_day` <= 100))),
  CONSTRAINT `ads_chk_3` CHECK (((`reward_per_like` >= 1) and (`reward_per_like` <= 100000)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ally_product_promotions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `premium_commercial_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKnoh37mwe3masc0oycslhsg3of` (`premium_commercial_id`,`product_id`),
  KEY `idx_ally_promo_premium_commercial` (`premium_commercial_id`),
  KEY `idx_ally_promo_product` (`product_id`),
  CONSTRAINT `FK1wey8xbsowkq1dct9x2psdegp` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKo4kwjkdrjnwtrqdv6kn4e50sq` FOREIGN KEY (`premium_commercial_id`) REFERENCES `commercial_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `answer_selected_options` (
  `answer_id` bigint NOT NULL,
  `option_id` bigint NOT NULL,
  KEY `FKhs90c9ssmhft40q82k526rtdx` (`option_id`),
  KEY `FKchclak4iqbrakkm3lqnoenkil` (`answer_id`),
  CONSTRAINT `FKchclak4iqbrakkm3lqnoenkil` FOREIGN KEY (`answer_id`) REFERENCES `survey_answers` (`id`),
  CONSTRAINT `FKhs90c9ssmhft40q82k526rtdx` FOREIGN KEY (`option_id`) REFERENCES `question_options` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `asset_definition_mime_types` (
  `asset_definition_id` bigint NOT NULL,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  KEY `FKbyn29g7a4lbg25x35aduxdeew` (`asset_definition_id`),
  CONSTRAINT `FKbyn29g7a4lbg25x35aduxdeew` FOREIGN KEY (`asset_definition_id`) REFERENCES `game_asset_definitions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `media_type` enum('AUDIO','DOCUMENT','IMAGE','MODEL','VIDEO') NOT NULL,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(255) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `asset_definition_id` bigint DEFAULT NULL,
  `branding_request_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5un42xd9ery903pb8rxm912od` (`asset_definition_id`),
  KEY `FK98nrruk0gyx6qwaatfetx075l` (`branding_request_id`),
  CONSTRAINT `FK5un42xd9ery903pb8rxm912od` FOREIGN KEY (`asset_definition_id`) REFERENCES `game_asset_definitions` (`id`),
  CONSTRAINT `FK98nrruk0gyx6qwaatfetx075l` FOREIGN KEY (`branding_request_id`) REFERENCES `branding_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(100) NOT NULL,
  `additional_data` text,
  `category` varchar(50) DEFAULT NULL,
  `class_name` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `entity_id` bigint DEFAULT NULL,
  `entity_type` varchar(100) DEFAULT NULL,
  `exception_message` text,
  `execution_time_ms` bigint DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `level` enum('CRITICAL','DEBUG','INFO','WARNING') NOT NULL,
  `method_name` varchar(255) DEFAULT NULL,
  `new_values` json DEFAULT NULL,
  `old_values` json DEFAULT NULL,
  `params` text,
  `result` text,
  `session_id` varchar(255) DEFAULT NULL,
  `stack_trace` text,
  `success` bit(1) NOT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `user_email` varchar(255) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `username` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_user_action` (`user_id`,`action`,`created_at`),
  KEY `idx_audit_level_date` (`level`,`created_at`),
  KEY `idx_audit_category` (`category`,`created_at`),
  KEY `idx_audit_entity` (`entity_type`,`entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `avatars` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `name` varchar(60) NOT NULL,
  `sort_order` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqke58dhtl74s5cxtc46uhqt2y` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `background_checks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `check_id` varchar(60) DEFAULT NULL,
  `check_type` enum('COMPANY','PERSON') NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `contract_id` bigint NOT NULL,
  `country` varchar(5) DEFAULT NULL,
  `pdf_report_url` varchar(500) DEFAULT NULL,
  `requested_at` datetime(6) NOT NULL,
  `requested_by_officer_id` bigint DEFAULT NULL,
  `score` double DEFAULT NULL,
  `status` enum('COMPLETED','DELAYED','ERROR','IN_PROGRESS','NOT_STARTED') NOT NULL,
  `subject_document` varchar(30) DEFAULT NULL,
  `subject_name` varchar(200) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmrt300dqbfl822ytjnykwqx8l` (`check_id`),
  KEY `idx_background_check_contract_id` (`contract_id`),
  KEY `idx_background_check_check_id` (`check_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `branding_request_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_name` varchar(200) NOT NULL,
  `author_role` enum('ADMIN','COMMERCIAL','DESIGNER') NOT NULL,
  `author_user_id` bigint NOT NULL,
  `content` varchar(2000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `related_status` enum('APPROVED','CAMPAIGN_CREATED','CANCELLED','CHANGES_REQUESTED','DESIGN_IN_PROGRESS','DRAFT','PENDING_ADVERTISER_APPROVAL','PENDING_REVIEW','REJECTED') DEFAULT NULL,
  `branding_request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK662n68xmuo5m9w4ryraqk3vvy` (`branding_request_id`),
  CONSTRAINT `FK662n68xmuo5m9w4ryraqk3vvy` FOREIGN KEY (`branding_request_id`) REFERENCES `branding_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `branding_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_notes` varchar(1000) DEFAULT NULL,
  `brand_description` varchar(1000) NOT NULL,
  `brand_name` varchar(200) NOT NULL,
  `budget_cents` bigint NOT NULL,
  `campaign_goal` enum('APP_INSTALLS','BRAND_AWARENESS','PRODUCT_PROMOTION','WEBSITE_TRAFFIC') DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `draft_form_data` json DEFAULT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `game_config` json DEFAULT NULL,
  `max_sessions_per_user_per_day` int DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `status` enum('APPROVED','CAMPAIGN_CREATED','CANCELLED','CHANGES_REQUESTED','DESIGN_IN_PROGRESS','DRAFT','PENDING_ADVERTISER_APPROVAL','PENDING_REVIEW','REJECTED') NOT NULL,
  `target_url` varchar(500) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `assigned_designer_id` bigint DEFAULT NULL,
  `campaign_id` bigint DEFAULT NULL,
  `commercial_id` bigint NOT NULL,
  `game_id` bigint NOT NULL,
  `game_config_definition_id` bigint NOT NULL,
  `reviewed_by_admin_id` bigint DEFAULT NULL,
  `target_audience_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhc02pn6hapunx854caiknuwdx` (`campaign_id`),
  KEY `idx_branding_commercial` (`commercial_id`),
  KEY `idx_branding_status` (`status`),
  KEY `idx_branding_designer` (`assigned_designer_id`),
  KEY `FK3nundikstl4nqkr8dn6kt6u5t` (`game_id`),
  KEY `FK8veek6ll274nhnu8a0g4xq4ke` (`game_config_definition_id`),
  KEY `FK15u1469t1n403cxo04s6xh3vs` (`reviewed_by_admin_id`),
  KEY `FKhhxnkvv84vs77xta3knkrfe8m` (`target_audience_id`),
  CONSTRAINT `FK15u1469t1n403cxo04s6xh3vs` FOREIGN KEY (`reviewed_by_admin_id`) REFERENCES `admin_details` (`user_id`),
  CONSTRAINT `FK3nundikstl4nqkr8dn6kt6u5t` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`),
  CONSTRAINT `FK56tgl417akqvakskqy838efgb` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `FK8veek6ll274nhnu8a0g4xq4ke` FOREIGN KEY (`game_config_definition_id`) REFERENCES `game_config_definitions` (`id`),
  CONSTRAINT `FKhhxnkvv84vs77xta3knkrfe8m` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`),
  CONSTRAINT `FKhy0adns6o94u80acdokutd4kf` FOREIGN KEY (`assigned_designer_id`) REFERENCES `game_designer_details` (`user_id`),
  CONSTRAINT `FKmuw7k9ononpkepfw2k5p6hhof` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `budget_transactions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount_cents` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `reference_id` varchar(255) DEFAULT NULL,
  `type` enum('AD_VIEW','BRANDING_REQUEST','GAME_REWARD','MANUAL_ADJUSTMENT') NOT NULL,
  `wallet_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhcgdpcqrvr6llt0rq0srd8umx` (`wallet_id`),
  CONSTRAINT `FKhcgdpcqrvr6llt0rq0srd8umx` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `campaigns` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `average_reward_per_session_cents` bigint NOT NULL,
  `budget_cents` bigint NOT NULL,
  `completed_sessions` bigint DEFAULT NULL,
  `completion_reward_cents` bigint NOT NULL,
  `config_data` json NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `max_reward_per_session_cents` bigint NOT NULL,
  `max_session_per_user_per_day` int DEFAULT NULL,
  `score_reward_factor` double NOT NULL,
  `sessions_played` bigint DEFAULT NULL,
  `spent_cents` bigint NOT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','CANCELLED','COMPLETED','DRAFT','PAUSED') NOT NULL,
  `total_play_time_seconds` bigint DEFAULT NULL,
  `unique_players_count` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `version` bigint DEFAULT NULL,
  `commercial_id` bigint NOT NULL,
  `config_definition_id` bigint NOT NULL,
  `game_id` bigint NOT NULL,
  `target_audience_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_campaign_game` (`game_id`),
  KEY `idx_campaign_commercial` (`commercial_id`),
  KEY `idx_campaign_status` (`status`),
  KEY `FKs0e9xcqvd1r5bclg7sl7vowcf` (`config_definition_id`),
  KEY `FKeftl1oprbjtevfxye85i8l1wi` (`target_audience_id`),
  CONSTRAINT `FKbnte97b853wfl6bqg4iggqb0q` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`),
  CONSTRAINT `FKeftl1oprbjtevfxye85i8l1wi` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`),
  CONSTRAINT `FKs0e9xcqvd1r5bclg7sl7vowcf` FOREIGN KEY (`config_definition_id`) REFERENCES `game_config_definitions` (`id`),
  CONSTRAINT `FKtkh0m662ifm729q2nc7q39y9s` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `catalog_integration_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_notes` varchar(1000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) NOT NULL,
  `desired_effects` varchar(1000) NOT NULL,
  `image_object_key` varchar(300) DEFAULT NULL,
  `item_draft` json DEFAULT NULL,
  `product_name` varchar(100) NOT NULL,
  `rejection_reason` varchar(500) DEFAULT NULL,
  `result_catalog_item_id` bigint DEFAULT NULL,
  `status` enum('APPROVED','COMPLETED','IN_REVIEW','ITEM_IN_PROGRESS','PENDING','REJECTED') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `assigned_designer_id` bigint DEFAULT NULL,
  `commercial_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5jfxtrdtemrnu3vc79ufbmowv` (`assigned_designer_id`),
  KEY `FKy2xfp1nhmsegogqkg3sqdkel` (`commercial_id`),
  CONSTRAINT `FK5jfxtrdtemrnu3vc79ufbmowv` FOREIGN KEY (`assigned_designer_id`) REFERENCES `game_designer_details` (`user_id`),
  CONSTRAINT `FKy2xfp1nhmsegogqkg3sqdkel` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `catalog_request_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_name` varchar(200) NOT NULL,
  `author_role` enum('ADMIN','COMMERCIAL','DESIGNER') NOT NULL,
  `author_user_id` bigint NOT NULL,
  `content` varchar(2000) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `related_status` enum('APPROVED','COMPLETED','IN_REVIEW','ITEM_IN_PROGRESS','PENDING','REJECTED') DEFAULT NULL,
  `catalog_request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrv0n53ppd482ycan2e2gkf09j` (`catalog_request_id`),
  CONSTRAINT `FKrv0n53ppd482ycan2e2gkf09j` FOREIGN KEY (`catalog_request_id`) REFERENCES `catalog_integration_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `categories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt8o6pivur7nn124jehx7cygw5` (`name`),
  KEY `idx_category_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_contracts` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `admin_decision_notes` varchar(1000) DEFAULT NULL,
  `admin_reviewed_at` datetime(6) DEFAULT NULL,
  `admin_reviewer_user_id` bigint DEFAULT NULL,
  `amount_cents_snapshot` bigint DEFAULT NULL,
  `business_approved_at` datetime(6) DEFAULT NULL,
  `esignature_envelope_id` varchar(200) DEFAULT NULL,
  `esignature_provider` varchar(30) DEFAULT NULL,
  `esignature_sent_at` datetime(6) DEFAULT NULL,
  `esignature_signed_at` datetime(6) DEFAULT NULL,
  `esignature_signer_email` varchar(255) DEFAULT NULL,
  `generated_at` datetime(6) NOT NULL,
  `object_key` varchar(500) NOT NULL,
  `purpose` enum('ONBOARDING','PLAN_CHANGE','RECHARGE') NOT NULL,
  `status` enum('APPROVED','CANCELLED','PENDING_BUSINESS_REVIEW','PENDING_SIGNATURE','PENDING_VERYGANA_REVIEW','REJECTED','SIGNED') NOT NULL,
  `version` int NOT NULL,
  `commercial_id` bigint NOT NULL,
  `investment_id` bigint DEFAULT NULL,
  `commercial_onboarding_id` bigint DEFAULT NULL,
  `subscription_id` binary(16) DEFAULT NULL,
  `target_plan_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKotf3wwrepx9drwltxhkrpd62u` (`investment_id`),
  UNIQUE KEY `UK2hlk8vi9t2te8d437e6hc03h8` (`commercial_onboarding_id`),
  UNIQUE KEY `UKirn4taf0njnth2wditk7b1gq9` (`subscription_id`),
  KEY `FKrtbiibcxre2mqcf0rjaw35a7p` (`commercial_id`),
  KEY `FKe5wleqhv5t301b6lxg2y7gr3j` (`target_plan_id`),
  CONSTRAINT `FKe5wleqhv5t301b6lxg2y7gr3j` FOREIGN KEY (`target_plan_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKiv13ryoa8cgdmghjn5q39i1m0` FOREIGN KEY (`investment_id`) REFERENCES `investments` (`id`),
  CONSTRAINT `FKowo83vcsb99juvq67cbsh3cce` FOREIGN KEY (`subscription_id`) REFERENCES `subscriptions` (`id`),
  CONSTRAINT `FKrtbiibcxre2mqcf0rjaw35a7p` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `FKs6vpo04fqfbxdwlex9rb3vhdi` FOREIGN KEY (`commercial_onboarding_id`) REFERENCES `commercial_onboarding` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_details` (
  `annual_income_range` enum('FROM_5000_TO_50000_SMMLV','FROM_500_TO_5000_SMMLV','LESS_THAN_500_SMMLV','MORE_THAN_50000_SMMLV') DEFAULT NULL,
  `ciiu_code` varchar(10) DEFAULT NULL,
  `company_name` varchar(200) DEFAULT NULL,
  `department_name` varchar(100) DEFAULT NULL,
  `legal_rep_doc_number` varchar(20) DEFAULT NULL,
  `legal_rep_doc_type` enum('CC','CE','PP') DEFAULT NULL,
  `mercantile_registration` varchar(20) DEFAULT NULL,
  `municipality_name` varchar(100) DEFAULT NULL,
  `nit` varchar(20) DEFAULT NULL,
  `is_pep` bit(1) NOT NULL,
  `user_id` bigint NOT NULL,
  `current_plan_id` bigint DEFAULT NULL,
  `default_payout_method_id` bigint DEFAULT NULL,
  `municipality_code` varchar(5) DEFAULT NULL,
  `commercial_activity_type` enum('PRODUCTS','SERVICES') DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKn99lptweu9cfehhowcxfaeo2i` (`mercantile_registration`),
  UNIQUE KEY `UKolvyrx8c8xp5dc5kpmap2vpch` (`nit`),
  UNIQUE KEY `UK2dgujp6kyik7fuahqtfpnx0ja` (`default_payout_method_id`),
  KEY `FKmjih71dh8hyfve5fp9lw23qe9` (`current_plan_id`),
  KEY `FKtk1j06si0grectao6xawlabsg` (`municipality_code`),
  CONSTRAINT `FK3ko42eobfgsgc8xrrqo905bbn` FOREIGN KEY (`user_id`) REFERENCES `user_details` (`user_id`),
  CONSTRAINT `FKmjih71dh8hyfve5fp9lw23qe9` FOREIGN KEY (`current_plan_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKn5tybjw74bxub5j4lfguyqor6` FOREIGN KEY (`default_payout_method_id`) REFERENCES `payout_methods` (`id`),
  CONSTRAINT `FKtk1j06si0grectao6xawlabsg` FOREIGN KEY (`municipality_code`) REFERENCES `municipality` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_type` enum('CAMARA_COMERCIO','CEDULA_REPRESENTANTE','CERTIFICACION_BANCARIA','MARCA_REGISTRADA','OTRO','PERMISO_SECTORIAL','RUT') NOT NULL,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `commercial_onboarding_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkqknrh78gtuykd6ayelt382yw` (`object_key`),
  KEY `idx_commercial_documents_onboarding` (`commercial_onboarding_id`),
  CONSTRAINT `FK9mdaylvocjrqt4249t9gi18h4` FOREIGN KEY (`commercial_onboarding_id`) REFERENCES `commercial_onboarding` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_onboarding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `address` varchar(300) DEFAULT NULL,
  `can_advertise_override` bit(1) DEFAULT NULL,
  `can_have_pets_override` bit(1) DEFAULT NULL,
  `can_promote_ally_products_override` bit(1) DEFAULT NULL,
  `can_sell_directly_override` bit(1) DEFAULT NULL,
  `can_use_games_override` bit(1) DEFAULT NULL,
  `can_use_surveys_override` bit(1) DEFAULT NULL,
  `classified_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `contract_duration_months` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_step` enum('ADVISOR_CONTACT_PENDING','BUSINESS_REVIEW_PENDING','CLASSIFICATION_PENDING','COMPLETED','CONTRACT_PENDING','DIAGNOSTIC_PENDING','DOCUMENTS_PENDING','LEGAL_IDENTIFICATION_PENDING','PAYMENT_PENDING','PLAN_PENDING','SIGNATURE_PENDING','TERMS_PENDING','VERYGANA_REVIEW_PENDING') NOT NULL,
  `diag_accepts_data_protection_metrics` enum('NECESITA_EXPLICACION','NO','SI') DEFAULT NULL,
  `diag_accepts_premium_brand_focus` enum('NECESITA_EXPLICACION','NO','SI') DEFAULT NULL,
  `diag_accepts_start_without_own_games_or_intelligence` bit(1) DEFAULT NULL,
  `diag_accepts_type_a_commission` enum('NO','QUIERE_EJEMPLO','SI') DEFAULT NULL,
  `diag_accepts_type_a_keys` enum('NO','QUIERE_EJEMPLO','SI') DEFAULT NULL,
  `diag_accepts_type_b_keys` enum('NO','QUIERE_EJEMPLO','SI') DEFAULT NULL,
  `diag_advertising_leadership` enum('AGENCIA_EXTERNA','AREAS_Y_AGENCIAS','AREA_MERCADEO','EMPLEADO_MULTIFUNCION','PROPIETARIO','SIN_MERCADEO') DEFAULT NULL,
  `diag_articulates_institutional_functions` enum('NO','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_can_accredit_network` enum('NO','NO_APLICA','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_can_approve_institutional_budgets` enum('DEPENDE_APROBACION','NO','SI') DEFAULT NULL,
  `diag_can_convene_and_sponsor_chain` enum('DEPENDE_APROBACION','NO','SI') DEFAULT NULL,
  `diag_can_convene_distributors` enum('NO','NO_APLICA','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_can_demonstrate_network` enum('NO','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_can_handle_orders_and_claims` enum('NO','NO_VENTA_DIRECTA','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_can_keep_listings_updated` enum('NO','NO_APLICA','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_can_provide_authorized_content` enum('NO','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_can_support_distributor_campaigns` enum('DEPENDE_PRESUPUESTO','NO','NO_APLICA','SI') DEFAULT NULL,
  `diag_current_reach` enum('BARRIO','DEPARTAMENTO','MUNICIPIO','OTROS_PAISES','PAIS','VARIOS_DEPARTAMENTOS','VARIOS_MUNICIPIOS') DEFAULT NULL,
  `diag_delivery_method` enum('DIGITAL','PRESENCIAL','RETIRO_ESTABLECIMIENTO','SIN_VENTA_DIRECTA','TRANSPORTE_EMPRESA','TRANSPORTE_USUARIO') DEFAULT NULL,
  `diag_desired_active_offers` enum('DE_4_A_10','HASTA_TRES','MAS_DE_10','NO_VENTA_DIRECTA') DEFAULT NULL,
  `diag_differentiated_responsibilities` enum('NO','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_direct_sale_to_consumer` enum('COMBINA','NO','SI') DEFAULT NULL,
  `diag_growth_depends_on_own_points` enum('COMBINAMOS','NO','SI') DEFAULT NULL,
  `diag_independent_entrepreneurs_help` enum('CASOS_AISLADOS','NO','SI') DEFAULT NULL,
  `diag_lacks_institutional_sponsor_network` enum('NO','NO_SEGURO','SI') DEFAULT NULL,
  `diag_main_activity` enum('COMERCIO_MINORISTA','COMERCIO_PRODUCTOS','DISTRIBUCION_MAYORISTA','OTRA','PRODUCCION','RESTAURANTE','SERVICIOS') DEFAULT NULL,
  `diag_market_reach_structure` enum('AREAS_INTERNAS','COMBINACION','EQUIPO_PEQUENO','PROPIETARIO','PROVEEDORES_ALIADOS') DEFAULT NULL,
  `diag_metrics_needed` enum('AVANZADAS','BASICAS','JUEGOS_CAMPANAS','NINGUNA') DEFAULT NULL,
  `diag_needs_more_capacity_than_type_a` bit(1) DEFAULT NULL,
  `diag_network_relationship_organized` enum('NO','NO_APLICA','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_own_sales_points` enum('DE_2_A_5','DE_6_A_20','MAS_DE_20','NINGUNO','UNO') DEFAULT NULL,
  `diag_products_reach_via_network` enum('NO_APLICA','NO_PUNTOS_PROPIOS','SI_PARCIALMENTE','SI_PRINCIPALMENTE') DEFAULT NULL,
  `diag_sells_directly_and_concentrated` enum('NO','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_stable_network_reaches_consumer` enum('NO','PARCIALMENTE','SI') DEFAULT NULL,
  `diag_three_offers_and_basic_metrics_sufficient` bit(1) DEFAULT NULL,
  `diag_type_a_monthly_fee_viable` enum('NO','QUIERE_INFO','SI') DEFAULT NULL,
  `diag_type_b_investment_capacity` enum('DEPENDE','NO','SI') DEFAULT NULL,
  `diag_understands_prosperity_regime` enum('NECESITA_EXPLICACION','NO','SI') DEFAULT NULL,
  `diag_will_recognize_interaction_values` enum('DEPENDE_TARIFA','NO','SI') DEFAULT NULL,
  `diagnostic_completed_at` datetime(6) DEFAULT NULL,
  `documents_completed_at` datetime(6) DEFAULT NULL,
  `economic_activity_description` varchar(500) DEFAULT NULL,
  `integration_details` varchar(1000) DEFAULT NULL,
  `investment_amount_cents_snapshot` bigint DEFAULT NULL,
  `legal_identification_completed_at` datetime(6) DEFAULT NULL,
  `legal_rep_first_name` varchar(100) DEFAULT NULL,
  `legal_rep_last_name` varchar(100) DEFAULT NULL,
  `max_ads_override` int DEFAULT NULL,
  `max_branded_games_override` int DEFAULT NULL,
  `max_investment_cents_snapshot` bigint DEFAULT NULL,
  `max_keys_pct_snapshot` int DEFAULT NULL,
  `max_products_override` int DEFAULT NULL,
  `max_surveys_override` int DEFAULT NULL,
  `min_investment_cents_snapshot` bigint DEFAULT NULL,
  `monthly_fee_cents_snapshot` bigint DEFAULT NULL,
  `person_type` enum('JURIDICA','NATURAL') DEFAULT NULL,
  `plan_accepted_at` datetime(6) DEFAULT NULL,
  `requires_special_negotiation` bit(1) DEFAULT NULL,
  `route` enum('A','B','C','D','E') DEFAULT NULL,
  `route_confirmed` bit(1) NOT NULL,
  `route_confirmed_at` datetime(6) DEFAULT NULL,
  `route_explanation` varchar(1000) DEFAULT NULL,
  `route_preliminary` bit(1) NOT NULL,
  `sale_commission_pct_snapshot` int DEFAULT NULL,
  `special_negotiation_details` varchar(1000) DEFAULT NULL,
  `special_negotiation_resolved_at` datetime(6) DEFAULT NULL,
  `terms_accepted_at` datetime(6) DEFAULT NULL,
  `terms_accepted_ip` varchar(64) DEFAULT NULL,
  `terms_accepted_user_agent` varchar(300) DEFAULT NULL,
  `terms_document_url` varchar(500) DEFAULT NULL,
  `terms_published_date` date DEFAULT NULL,
  `terms_version` varchar(20) DEFAULT NULL,
  `verification_required` bit(1) NOT NULL,
  `visibility_boost_pct_override` decimal(38,2) DEFAULT NULL,
  `commercial_details_id` bigint NOT NULL,
  `selected_plan_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK97vchgfn3u9n2qwmd7cr9obbw` (`commercial_details_id`),
  KEY `FKh93gc58joc4lgyic6qn8k77b2` (`selected_plan_id`),
  CONSTRAINT `FKh93gc58joc4lgyic6qn8k77b2` FOREIGN KEY (`selected_plan_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKhkqpw5ut85u4q04mbeuvf89q0` FOREIGN KEY (`commercial_details_id`) REFERENCES `commercial_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_onboarding_business_goals` (
  `commercial_onboarding_id` bigint NOT NULL,
  `business_goal` enum('AMPLIAR_RED_DISTRIBUCION','AUMENTAR_ROTACION','CAMPANAS_REGIONALES_NACIONALES','CONOCER_INTERES_PRODUCTOS','CONSEGUIR_CLIENTES','FIDELIZAR_JUEGOS','FORTALECER_DISTRIBUIDORES','INTELIGENCIA_COMERCIAL','MASCOTA_VIRTUAL','OTRO','PATROCINAR_EMPRESARIOS','POSICIONAMIENTO_MARCA','PROMOCIONAR_BAJA_ROTACION','RECIBIR_PATROCINIO','RECONOCIMIENTO_ESTABLECIMIENTO','RECUPERAR_CLIENTES','VENDER_PRODUCTOS','VENDER_SERVICIOS') DEFAULT NULL,
  `priority` int NOT NULL,
  PRIMARY KEY (`commercial_onboarding_id`,`priority`),
  CONSTRAINT `FKgahc3l8cb99clf99clqg47cke` FOREIGN KEY (`commercial_onboarding_id`) REFERENCES `commercial_onboarding` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_onboarding_growth_tools` (
  `commercial_onboarding_id` bigint NOT NULL,
  `growth_tool` enum('CURIOSIDAD_NEUROCIENCIA','DESCUBRIR_INTENCION_COMPRA','ECOSISTEMA_CONSUMIDOR','EMOCION_OFERTA','FORTALECER_RED_CAMPANAS','IMPULSAR_ALIADOS_PROMOCIONES','LLAVES_PROMOCIONALES','PATROCINAR_EMPRESAS_AB','POSICIONAMIENTO_EXPERIENCIAS','PROMOCIONAR_BAJA_ROTACION','PROMOCIONES_MEMORABLES','RECOMPRA_FRECUENCIA','RESPALDO_PATROCINADOR') DEFAULT NULL,
  `priority` int NOT NULL,
  PRIMARY KEY (`commercial_onboarding_id`,`priority`),
  CONSTRAINT `FKdhlqtc85t869oeiddspupqnir` FOREIGN KEY (`commercial_onboarding_id`) REFERENCES `commercial_onboarding` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_onboarding_institutional_tools` (
  `commercial_onboarding_id` bigint NOT NULL,
  `institutional_tool` enum('CONTRATOS_DISTRIBUCION','ESTATUTOS','JUNTA_DECISION','MANUALES','NINGUNA','ORGANIGRAMA','POLITICAS_COMERCIALES','PRESUPUESTO_MERCADEO','SISTEMAS_INFORMACION') DEFAULT NULL,
  KEY `FKmrx5pko3o6r38iayqx46rxskt` (`commercial_onboarding_id`),
  CONSTRAINT `FKmrx5pko3o6r38iayqx46rxskt` FOREIGN KEY (`commercial_onboarding_id`) REFERENCES `commercial_onboarding` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_onboarding_network_actors` (
  `commercial_onboarding_id` bigint NOT NULL,
  `network_actor` enum('CONCESIONARIOS','DISTRIBUIDORES','DROGUERIAS','FERRETERIAS','FRANQUICIADOS','MAYORISTAS','NINGUNO','OTROS_EMPRESARIOS','RESTAURANTES','SUPERMERCADOS','TIENDAS') DEFAULT NULL,
  KEY `FKruq70ghq06dc6hmar0vvxlk2b` (`commercial_onboarding_id`),
  CONSTRAINT `FKruq70ghq06dc6hmar0vvxlk2b` FOREIGN KEY (`commercial_onboarding_id`) REFERENCES `commercial_onboarding` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_onboarding_tech_needs` (
  `commercial_onboarding_id` bigint NOT NULL,
  `tech_need` enum('ACTIVACION_AUTOMATICA','API','CONCILIACION') DEFAULT NULL,
  KEY `FKew73mhn8bj2s6yg05kgkqqkgn` (`commercial_onboarding_id`),
  CONSTRAINT `FKew73mhn8bj2s6yg05kgkqqkgn` FOREIGN KEY (`commercial_onboarding_id`) REFERENCES `commercial_onboarding` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `commercial_page_visits` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `source` enum('AD','GAME','OTHER','PROFILE','SURVEY') NOT NULL,
  `target_url` varchar(500) DEFAULT NULL,
  `user_hash` varchar(64) DEFAULT NULL,
  `ad_id` bigint DEFAULT NULL,
  `commercial_id` bigint NOT NULL,
  `consumer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_page_visit_commercial_created` (`commercial_id`,`created_at`),
  KEY `idx_page_visit_ad` (`ad_id`),
  KEY `FKayxj4sv2m027cn2gbhwmx4jaw` (`consumer_id`),
  CONSTRAINT `FKayxj4sv2m027cn2gbhwmx4jaw` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FKbjy7nhg7i7k21x3ok67i6n60o` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `FKkru5omw318w637h92qjg033fn` FOREIGN KEY (`ad_id`) REFERENCES `ads` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_officer_details` (
  `badge_number` varchar(20) NOT NULL,
  `last_name` varchar(100) NOT NULL,
  `name` varchar(100) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKuuwwc0uug9b9nccxgaih9kra` (`badge_number`),
  CONSTRAINT `FKfon95oesffe5862dwfsjuhxn` FOREIGN KEY (`user_id`) REFERENCES `user_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consumer_details` (
  `ads_watched` int DEFAULT NULL,
  `age` int DEFAULT NULL,
  `daily_ad_count` int DEFAULT NULL,
  `department_name` varchar(50) NOT NULL,
  `document_number` varchar(20) NOT NULL,
  `document_type` enum('CC','CE','PP') NOT NULL,
  `gender` enum('FEMALE','MALE','OTHER','PREFER_NOT_TO_SAY') DEFAULT NULL,
  `has_pet` bit(1) NOT NULL,
  `last_daily_login_date` datetime(6) DEFAULT NULL,
  `last_name` varchar(50) NOT NULL,
  `monthly_income_range` enum('FROM_1_TO_3_SMMLV','FROM_3_TO_10_SMMLV','LESS_THAN_1_SMMLV','MORE_THAN_10_SMMLV') DEFAULT NULL,
  `municipality_name` varchar(50) NOT NULL,
  `name` varchar(50) NOT NULL,
  `occupation` varchar(100) DEFAULT NULL,
  `is_pep` bit(1) NOT NULL,
  `referral_code` varchar(16) NOT NULL,
  `user_hash` varchar(64) NOT NULL,
  `user_name` varchar(20) NOT NULL,
  `user_id` bigint NOT NULL,
  `avatar_id` bigint NOT NULL,
  `municipality_code` varchar(5) NOT NULL,
  `referred_by_consumer_id` bigint DEFAULT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKjeywe2570u5jjqq8157q7wbyf` (`user_hash`),
  KEY `FKm37cpnkfce4q3mtn40qohuy6c` (`avatar_id`),
  KEY `FKd92nax0l8yfsci47ndiscogpp` (`municipality_code`),
  KEY `FKria3pul91ebxtupmkntof31ya` (`referred_by_consumer_id`),
  CONSTRAINT `FK2gyn042hy0b6h6wqqwws9ij3b` FOREIGN KEY (`user_id`) REFERENCES `user_details` (`user_id`),
  CONSTRAINT `FKd92nax0l8yfsci47ndiscogpp` FOREIGN KEY (`municipality_code`) REFERENCES `municipality` (`code`),
  CONSTRAINT `FKm37cpnkfce4q3mtn40qohuy6c` FOREIGN KEY (`avatar_id`) REFERENCES `avatars` (`id`),
  CONSTRAINT `FKria3pul91ebxtupmkntof31ya` FOREIGN KEY (`referred_by_consumer_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `consumer_details_chk_1` CHECK ((`ads_watched` >= 0)),
  CONSTRAINT `consumer_details_chk_2` CHECK (((`daily_ad_count` <= 100) and (`daily_ad_count` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `consumer_preferences` (
  `user_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  KEY `FKhd9i4te1545d56ib1j0ce8yn7` (`category_id`),
  KEY `FKeair84s0shsyi8dp04aca755v` (`user_id`),
  CONSTRAINT `FKeair84s0shsyi8dp04aca755v` FOREIGN KEY (`user_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FKhd9i4te1545d56ib1j0ce8yn7` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `copayments` (
  `id` binary(16) NOT NULL,
  `cash_amount_cents` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `keys_refunded_at` datetime(6) DEFAULT NULL,
  `keys_used` bigint NOT NULL,
  `keys_value_cents` bigint NOT NULL,
  `status` enum('COMPLETED','FAILED','KEYS_REFUNDED','PENDING','PROCESSING') NOT NULL,
  `total_amount_cents` bigint NOT NULL,
  `consumer_id` bigint NOT NULL,
  `purchase_id` bigint NOT NULL,
  `wompi_transaction_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK42u8t37or8if5i3knrgtdbf47` (`purchase_id`),
  UNIQUE KEY `UKsqxumirddk847t47qurox1qnt` (`wompi_transaction_id`),
  KEY `FKtd0uagfpy24wot7oo3l0h6s1f` (`consumer_id`),
  CONSTRAINT `FKgj42yhswn4gxycashdtm1oxmq` FOREIGN KEY (`wompi_transaction_id`) REFERENCES `wompi_transactions` (`id`),
  CONSTRAINT `FKn5rwa071s0apiqhem0b833ed5` FOREIGN KEY (`purchase_id`) REFERENCES `purchases` (`id`),
  CONSTRAINT `FKtd0uagfpy24wot7oo3l0h6s1f` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `corporate_resources` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content_type` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `object_key` varchar(255) NOT NULL,
  `original_file_name` varchar(255) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_by` bigint NOT NULL,
  `branding_request_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3rubxqsii39ne6r2rk6u5ra8o` (`branding_request_id`),
  CONSTRAINT `FK3rubxqsii39ne6r2rk6u5ra8o` FOREIGN KEY (`branding_request_id`) REFERENCES `branding_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department` (
  `code` varchar(2) NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnostic_question` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(10) NOT NULL,
  `depends_on_question_code` varchar(10) DEFAULT NULL,
  `depends_on_values` varchar(300) DEFAULT NULL,
  `display_order` int NOT NULL,
  `field_name` varchar(60) NOT NULL,
  `help_text` varchar(1500) DEFAULT NULL,
  `max_selections` int DEFAULT NULL,
  `ordered` bit(1) NOT NULL,
  `required` bit(1) NOT NULL,
  `question_text` varchar(500) NOT NULL,
  `type` enum('BOOLEAN','MULTI_CHOICE','SINGLE_CHOICE') NOT NULL,
  `section_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKei0tkmu90wdtv6xrso13d9mqu` (`section_id`),
  CONSTRAINT `FKei0tkmu90wdtv6xrso13d9mqu` FOREIGN KEY (`section_id`) REFERENCES `diagnostic_section` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnostic_question_option` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `display_order` int NOT NULL,
  `exclusive` bit(1) NOT NULL,
  `label` varchar(300) NOT NULL,
  `option_value` varchar(50) NOT NULL,
  `question_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8nay0pp7duagx24mrsj2oa0ie` (`question_id`),
  CONSTRAINT `FK8nay0pp7duagx24mrsj2oa0ie` FOREIGN KEY (`question_id`) REFERENCES `diagnostic_question` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnostic_questionnaire` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `opening_message` varchar(1500) DEFAULT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2y69oyt8nnoyb71b1c8uqdl94` (`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnostic_questionnaire_actions` (
  `questionnaire_id` bigint NOT NULL,
  `action_label` varchar(120) DEFAULT NULL,
  `display_order` int NOT NULL,
  PRIMARY KEY (`questionnaire_id`,`display_order`),
  CONSTRAINT `FKgj1pi1d3knba99tf52q9mdj0u` FOREIGN KEY (`questionnaire_id`) REFERENCES `diagnostic_questionnaire` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `diagnostic_section` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(40) NOT NULL,
  `display_order` int NOT NULL,
  `subtitle` varchar(500) DEFAULT NULL,
  `title` varchar(150) NOT NULL,
  `questionnaire_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK80y3hpa3u1e9acfom3a1jiwqo` (`questionnaire_id`),
  CONSTRAINT `FK80y3hpa3u1e9acfom3a1jiwqo` FOREIGN KEY (`questionnaire_id`) REFERENCES `diagnostic_questionnaire` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_verification_codes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `attempts` int NOT NULL,
  `code_hash` varchar(100) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `email` varchar(255) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `favorite_products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `consumer_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKin5s376t2br2ovjeg3dsctdxq` (`consumer_id`,`product_id`),
  KEY `idx_consumer_id` (`consumer_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `FK66gqbpuhbuxns99w8sx4ksoke` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKaijffrbfahe6b0ei56llwdu4o` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `features` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `type` enum('AMOUNT','BOOLEAN','LIMIT','PERCENTAGE') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKciidl2qb5n7j58gjao351uybl` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_asset_definitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `aspect_ratio_height` int NOT NULL,
  `aspect_ratio_width` int NOT NULL,
  `asset_type` enum('BACKGROUND','BANNER','CARD_BACK','CARD_FRAME','CARD_IMAGE','COLLECTIBLE','HANGMAN_PROGRESS','ICON','LOGO','MUSIC','OBSTACLE','PARALLAX_LAYER','POWERUP_ICON','PUZZLE_PIECE','SFX','SOUNDTRACK','SOUND_EFFECT','SPRITE','TEXTURE','THUMBNAIL','WATERMARK') NOT NULL,
  `code` varchar(255) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `max_size_bytes` bigint NOT NULL,
  `media_type` enum('AUDIO','DOCUMENT','IMAGE','MODEL','VIDEO') NOT NULL,
  `multiple` bit(1) NOT NULL,
  `required` bit(1) NOT NULL,
  `required_height` int NOT NULL,
  `required_width` int NOT NULL,
  `game_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_game_asset_game` (`game_id`),
  KEY `idx_game_asset_type` (`asset_type`),
  CONSTRAINT `FKag35pvd5t0hvw2rn5oqrpeaad` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_config_definitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `average_duration_seconds` int NOT NULL,
  `average_reward_per_session_cents` bigint NOT NULL,
  `completion_reward_cents` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(100) DEFAULT NULL,
  `is_latest` bit(1) NOT NULL,
  `json_schema` json NOT NULL,
  `max_reward_per_session_cents` bigint NOT NULL,
  `score_reward_factor` double NOT NULL,
  `ui_schema` json DEFAULT NULL,
  `version` bigint NOT NULL,
  `game_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_game_version` (`game_id`,`version`),
  CONSTRAINT `FKa839jd7l9wmjtn8n3nqy42qe6` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_designer_details` (
  `active` bit(1) NOT NULL,
  `bio` varchar(500) DEFAULT NULL,
  `campaigns_designed` int NOT NULL,
  `designer_code` varchar(20) NOT NULL,
  `joined_at` date NOT NULL,
  `last_name` varchar(100) NOT NULL,
  `name` varchar(100) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `UKerk7s9loq1m4vovqsvbent2at` (`designer_code`),
  CONSTRAINT `FK8p02mkjvxgp8719kjse7j348u` FOREIGN KEY (`user_id`) REFERENCES `user_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_metric_definitions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `metric_key` varchar(255) NOT NULL,
  `required_flag` bit(1) NOT NULL,
  `metric_type` varchar(255) NOT NULL,
  `game_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtnura9xbo449n2ikxfd4wnwrk` (`game_id`),
  CONSTRAINT `FKtnura9xbo449n2ikxfd4wnwrk` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_session_metrics` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `metric_key` varchar(255) NOT NULL,
  `metric_type` enum('BOOLEAN','DECIMAL','DOUBLE','INT','STRING') NOT NULL,
  `metric_value` json NOT NULL,
  `recorded_at` datetime(6) DEFAULT NULL,
  `unit` varchar(255) DEFAULT NULL,
  `session_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3cy8a8m4ikkraatisi8vbwehr` (`session_id`),
  CONSTRAINT `FK3cy8a8m4ikkraatisi8vbwehr` FOREIGN KEY (`session_id`) REFERENCES `game_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `game_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `coins_earned` bigint DEFAULT NULL,
  `completed` bit(1) NOT NULL,
  `device_platform` enum('MOBILE','PC','TABLET') NOT NULL,
  `end_time` datetime(6) DEFAULT NULL,
  `play_time_seconds` bigint DEFAULT NULL,
  `reward_granted` bit(1) NOT NULL,
  `score` int DEFAULT NULL,
  `session_token` varchar(100) DEFAULT NULL,
  `start_time` datetime(6) NOT NULL,
  `user_hash` varchar(255) NOT NULL,
  `campaign_id` bigint NOT NULL,
  `consumer_id` bigint NOT NULL,
  `game_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKc58qk6vyk42uakuixsca7skqs` (`session_token`),
  KEY `FK31p86odtregqpog5omlk2wdby` (`campaign_id`),
  KEY `FK3sdbrqueo4sj24bmfo6lqtddc` (`consumer_id`),
  KEY `FKlg198vj4h7ejkp6n710neylxx` (`game_id`),
  CONSTRAINT `FK31p86odtregqpog5omlk2wdby` FOREIGN KEY (`campaign_id`) REFERENCES `campaigns` (`id`),
  CONSTRAINT `FK3sdbrqueo4sj24bmfo6lqtddc` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FKlg198vj4h7ejkp6n710neylxx` FOREIGN KEY (`game_id`) REFERENCES `games` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `games` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `delivery_type` enum('PATH','QUERY') DEFAULT NULL,
  `description` varchar(255) NOT NULL,
  `front_page_url` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `url` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_game_active` (`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `impact_stories` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_name` varchar(150) DEFAULT NULL,
  `beneficiaries_count` int NOT NULL,
  `category` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` text NOT NULL,
  `invested_amount` decimal(15,2) DEFAULT NULL,
  `invested_currency` varchar(10) DEFAULT NULL,
  `location` varchar(100) DEFAULT NULL,
  `status` enum('ARCHIVED','DELETED','DRAFT','PUBLISHED') NOT NULL,
  `story_date` date NOT NULL,
  `tags` varchar(500) DEFAULT NULL,
  `title` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `investments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `confirmed` bit(1) NOT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deposit_amount_cents` bigint NOT NULL,
  `failed_at` datetime(6) DEFAULT NULL,
  `wompi_reference` varchar(100) DEFAULT NULL,
  `plan_at_deposit_id` bigint NOT NULL,
  `wallet_id` bigint NOT NULL,
  `wompi_transaction_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfb6igkl68bmyqmcgrf7u7yvgk` (`wompi_reference`),
  UNIQUE KEY `UKtk4p7n5lhd4aoapitfve15p81` (`wompi_transaction_id`),
  KEY `idx_inv_wallet_id` (`wallet_id`),
  KEY `idx_inv_wompi_reference` (`wompi_reference`),
  KEY `FK8u98mv7iiy9n82lwghh0fx15j` (`plan_at_deposit_id`),
  CONSTRAINT `FK8u98mv7iiy9n82lwghh0fx15j` FOREIGN KEY (`plan_at_deposit_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKbs2h04mppijgqirac7bdvow3i` FOREIGN KEY (`wompi_transaction_id`) REFERENCES `wompi_transactions` (`id`),
  CONSTRAINT `FKoa906fn055fx7p2fq6wklx5u0` FOREIGN KEY (`wallet_id`) REFERENCES `wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `key_transactions` (
  `id` binary(16) NOT NULL,
  `connectivity_keys_delta_cents` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `expiry_processed` bit(1) NOT NULL,
  `pet_catalog_item_id` bigint DEFAULT NULL,
  `purchase_keys_delta_cents` bigint DEFAULT NULL,
  `reason` varchar(255) NOT NULL,
  `reference_id` binary(16) NOT NULL,
  `type` enum('CREDIT_ADMIN_ADJUSTMENT','CREDIT_COPAYMENT_REFUND','CREDIT_INTERACTION','CREDIT_REFERRAL_BONUS','DEBIT_ADMIN_ADJUSTMENT','DEBIT_CONNECTIVITY_RECHARGE','DEBIT_COPAYMENT','DEBIT_PET_GAME','EXPIRED','RELEASE_COPAYMENT_CANCELLED','RESERVE_COPAYMENT_PENDING') NOT NULL,
  `key_wallet_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kt_wallet_id` (`key_wallet_id`),
  KEY `idx_kt_type` (`type`),
  KEY `idx_kt_reference_id` (`reference_id`),
  KEY `idx_kt_expires_at` (`expires_at`),
  KEY `idx_kt_expiry_processed` (`expiry_processed`),
  KEY `idx_kt_created_at` (`created_at`),
  CONSTRAINT `FKi9506r5bia7sohbs3u4qfmuh1` FOREIGN KEY (`key_wallet_id`) REFERENCES `key_wallets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `key_wallets` (
  `id` binary(16) NOT NULL,
  `blocked_connectivity_keys_cents` bigint NOT NULL,
  `blocked_purchase_keys_cents` bigint NOT NULL,
  `connectivity_keys_cents` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `purchase_keys_cents` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `consumer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKse2h5sql7slavhnd74ncaoe1l` (`consumer_id`),
  CONSTRAINT `FKiuaghrf5eewrg84rbllwt1mh3` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legal_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `document_url` varchar(500) NOT NULL,
  `object_key` varchar(500) NOT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `published_date` date NOT NULL,
  `size_bytes` bigint DEFAULT NULL,
  `status` enum('ORPHANED','PENDING','VALIDATED') NOT NULL,
  `type` enum('BASIC_PLAN_CONTRACT','BUSINESS_OWNER_TERMS_AND_CONDITIONS','COOKIES_POLICY','DATA_PROCESSING_POLICY','PREMIUM_PLAN_CONTRACT','PRIVACY_POLICY','STANDARD_PLAN_CONTRACT','USERS_TERMS_AND_CONDITIONS') NOT NULL,
  `version` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjmoclv8s119qw2up6hcty1ek` (`object_key`),
  KEY `idx_legal_documents_type_active` (`type`,`active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `mobile_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data_amount` int DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `price` double DEFAULT NULL,
  `provider` varchar(255) DEFAULT NULL,
  `validity_days` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `municipality` (
  `code` varchar(5) NOT NULL,
  `department_code` varchar(2) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`code`),
  UNIQUE KEY `UKhj4yq8ynq30v53ybxsyhnhcl7` (`department_code`,`name`),
  CONSTRAINT `FK2q87nbgegq4v1rpynudyq6uko` FOREIGN KEY (`department_code`) REFERENCES `department` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `date_sent` datetime(6) DEFAULT NULL,
  `is_read` bit(1) DEFAULT NULL,
  `message` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  `type` enum('EMAIL','IN_APP_NOTIFICATION','PUSH_NOTIFICATION','SMS') NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKar109xva43y9llsfabuqtnmde` (`user_id`),
  CONSTRAINT `FKar109xva43y9llsfabuqtnmde` FOREIGN KEY (`user_id`) REFERENCES `user_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `outbox_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `event_type` varchar(255) NOT NULL,
  `last_error` text,
  `payload` varchar(255) NOT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `retry_count` int NOT NULL,
  `status` enum('DONE','FAILED','PENDING','PROCESSING') NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_setup_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token` varchar(64) NOT NULL,
  `used` bit(1) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKta9ukbgikgq44fror9uv8430k` (`token`),
  UNIQUE KEY `UKqjwje63dkkf55034frh9bxyun` (`user_id`),
  CONSTRAINT `FK83mu55qqeue0lhpr5t20b8mdx` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payout_items` (
  `id` binary(16) NOT NULL,
  `amount_cents` bigint NOT NULL,
  `copayment_id` binary(16) NOT NULL,
  `payout_id` binary(16) NOT NULL,
  `purchase_item_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKke7b6hlhntoqa9eyjk2wf11nh` (`purchase_item_id`),
  KEY `FKkbyfd8w67jp8ewxdwaix154r3` (`copayment_id`),
  KEY `FK5vows29lpnh53pbsvafjngear` (`payout_id`),
  CONSTRAINT `FK5vows29lpnh53pbsvafjngear` FOREIGN KEY (`payout_id`) REFERENCES `payouts` (`id`),
  CONSTRAINT `FKkbyfd8w67jp8ewxdwaix154r3` FOREIGN KEY (`copayment_id`) REFERENCES `copayments` (`id`),
  CONSTRAINT `FKot5epmo03k24iwlffihuqo1qv` FOREIGN KEY (`purchase_item_id`) REFERENCES `purchase_items` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payout_method_certificate_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `payout_method_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKaltjiqql6td6ipito6o1k3kkg` (`object_key`),
  UNIQUE KEY `UKlddrsfaj5al5w8v7bl1pv5yl3` (`payout_method_id`),
  CONSTRAINT `FK8c0dsgalg2a5jwfvnc4tmelh6` FOREIGN KEY (`payout_method_id`) REFERENCES `payout_methods` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payout_methods` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_holder_doc` varchar(20) NOT NULL,
  `account_holder_doc_type` enum('CC','CE','NIT','PP','TI') NOT NULL,
  `account_holder_name` varchar(200) NOT NULL,
  `account_number` varchar(30) DEFAULT NULL,
  `active` bit(1) NOT NULL,
  `alias` varchar(100) NOT NULL,
  `bank_account_type` enum('CHECKING','SAVINGS') DEFAULT NULL,
  `bank_code` varchar(40) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `first_payout_completed` bit(1) NOT NULL,
  `phone_number` varchar(15) DEFAULT NULL,
  `rejection_reason` varchar(500) DEFAULT NULL,
  `type` enum('BANK_ACCOUNT','DAVIPLATA','NEQUI') NOT NULL,
  `verification_status` enum('AWAITING_OTP','PENDING_VERIFICATION','REJECTED','SUSPENDED','UNDER_REVIEW','VERIFIED') NOT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `commercial_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_payout_methods_commercial_id` (`commercial_id`),
  KEY `idx_payout_methods_verification_status` (`verification_status`),
  CONSTRAINT `FKmg6jbdlhi95sxdxn50o0ng3rw` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payouts` (
  `id` binary(16) NOT NULL,
  `commission_cents` bigint NOT NULL,
  `commission_pct_applied` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `gross_amount_cents` bigint NOT NULL,
  `net_amount_cents` bigint NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `period_end` datetime(6) NOT NULL,
  `period_start` datetime(6) NOT NULL,
  `retry_count` int NOT NULL,
  `scheduled_at` datetime(6) NOT NULL,
  `status` enum('EXHAUSTED','FAILED','PAID','PROCESSING','SCHEDULED') NOT NULL,
  `commercial_id` bigint NOT NULL,
  `wompi_transaction_id` binary(16) DEFAULT NULL,
  `commission_amount_cents` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkjfw1axp34uggsxu9hieiuj18` (`wompi_transaction_id`),
  KEY `FK20nc5v50ikinyjawqqlucbsxl` (`commercial_id`),
  CONSTRAINT `FK20nc5v50ikinyjawqqlucbsxl` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `FKptc1uc39pk1fb3138alfjduu6` FOREIGN KEY (`wompi_transaction_id`) REFERENCES `wompi_transactions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_catalog_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) DEFAULT NULL,
  `body_fat_delta` int DEFAULT NULL,
  `cures_all_parts` bit(1) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `energy_delta` int DEFAULT NULL,
  `exp_when_eating` int DEFAULT NULL,
  `external_id` int DEFAULT NULL,
  `health_delta` int DEFAULT NULL,
  `humor_delta` int DEFAULT NULL,
  `hunger_delta` int DEFAULT NULL,
  `hygiene_delta` int DEFAULT NULL,
  `is_drink` bit(1) DEFAULT NULL,
  `is_medicine` bit(1) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `price` int DEFAULT NULL,
  `sprite_object_key` varchar(255) DEFAULT NULL,
  `thirst_delta` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK917hd3co87bn1rkbkoison3c` (`external_id`),
  UNIQUE KEY `uk_pet_catalog_external_id` (`external_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) DEFAULT NULL,
  `button_label` varchar(255) DEFAULT NULL,
  `button_url` varchar(255) DEFAULT NULL,
  `date` date DEFAULT NULL,
  `external_id` varchar(255) DEFAULT NULL,
  `image_url` varchar(255) DEFAULT NULL,
  `message` varchar(1000) DEFAULT NULL,
  `is_read` bit(1) DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_player_saves` (
  `consumer_id` bigint NOT NULL,
  `data` mediumtext NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`consumer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_scene_objects` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `height` int DEFAULT NULL,
  `object_id` varchar(255) DEFAULT NULL,
  `object_key` varchar(255) DEFAULT NULL,
  `scale_multiplier` double DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `width` int DEFAULT NULL,
  `x` int DEFAULT NULL,
  `y` int DEFAULT NULL,
  `scene_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgs4t75au6iiidbgnme6jt2k82` (`scene_id`),
  CONSTRAINT `FKgs4t75au6iiidbgnme6jt2k82` FOREIGN KEY (`scene_id`) REFERENCES `pet_scenes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_scenes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) DEFAULT NULL,
  `scene_id` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pet_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `expired` bit(1) NOT NULL,
  `session_token` varchar(255) NOT NULL,
  `start_time` datetime(6) NOT NULL,
  `user_hash` varchar(255) NOT NULL,
  `consumer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKg131x9o1lxoicd5oms5y351f2` (`session_token`),
  KEY `FKtpduxjgricq2y9hrbxh8jukms` (`consumer_id`),
  CONSTRAINT `FKtpduxjgricq2y9hrbxh8jukms` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plan_change_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `applied_at` datetime(6) DEFAULT NULL,
  `rejection_acknowledged_at` datetime(6) DEFAULT NULL,
  `rejection_reason` text,
  `requested_at` datetime(6) NOT NULL,
  `requested_investment_amount_cents` bigint DEFAULT NULL,
  `required_top_up_amount_cents` bigint DEFAULT NULL,
  `status` enum('APPLIED','CANCELLED','CONTRACT_PENDING_REVIEW','CONTRACT_SIGNED','PAYMENT_PENDING','REJECTED','REQUESTED') NOT NULL,
  `commercial_id` bigint NOT NULL,
  `contract_id` bigint DEFAULT NULL,
  `from_plan_id` bigint NOT NULL,
  `to_plan_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKg4li98o6ebv0upb02yr1p9k8s` (`contract_id`),
  KEY `FKqf21e4x9db0vnci8u58npvus8` (`commercial_id`),
  KEY `FKp23h8ll2tfjmsjjlaomavjaq` (`from_plan_id`),
  KEY `FKcve71vj9vn6uv5gpf2vjg0vtc` (`to_plan_id`),
  CONSTRAINT `FKcve71vj9vn6uv5gpf2vjg0vtc` FOREIGN KEY (`to_plan_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKdls1sk360g7y5wlbbo7auaj8p` FOREIGN KEY (`contract_id`) REFERENCES `commercial_contracts` (`id`),
  CONSTRAINT `FKp23h8ll2tfjmsjjlaomavjaq` FOREIGN KEY (`from_plan_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKqf21e4x9db0vnci8u58npvus8` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plan_features` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `bool_value` bit(1) DEFAULT NULL,
  `decimal_value` decimal(10,4) DEFAULT NULL,
  `int_value` int DEFAULT NULL,
  `long_value` bigint DEFAULT NULL,
  `feature_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKey1ro3ghowrbifus9j827elk9` (`plan_id`,`feature_id`),
  KEY `FKo8ts170vjfng2wmqoqxhnotn4` (`feature_id`),
  CONSTRAINT `FKmii31u2imuu6cet94c3yv7c0p` FOREIGN KEY (`plan_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKo8ts170vjfng2wmqoqxhnotn4` FOREIGN KEY (`feature_id`) REFERENCES `features` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `plans` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `code` enum('BASIC','PREMIUM','STANDARD') NOT NULL,
  `description` text,
  `max_investment_cents` bigint DEFAULT NULL,
  `max_keys_pct` int NOT NULL,
  `min_investment_cents` bigint DEFAULT NULL,
  `monthly_price_cents` bigint DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `sale_commission_pct` int NOT NULL,
  `version` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3y5a0258j10e3mnhcoiue7ab2` (`version`,`code`),
  UNIQUE KEY `UKbsiq2g7uq9l49v27bsijicsys` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pqrs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `description` text NOT NULL,
  `due_date` datetime(6) NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `response` text,
  `status` enum('CERRADA','EN_REVISION','PENDIENTE_ASIGNACION','PENDIENTE_PAGO_REEMBOLSO','RECIBIDA','RESUELTA') NOT NULL,
  `subject` varchar(200) NOT NULL,
  `type` enum('PETICION','QUEJA','RECLAMO','SUGERENCIA') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `assigned_admin_id` bigint DEFAULT NULL,
  `requester_id` bigint NOT NULL,
  `action` enum('DISMISS','REFUND') DEFAULT NULL,
  `reason_code` enum('CODE_INVALID','NOT_AS_DESCRIBED','NOT_DELIVERED','OTHER') DEFAULT NULL,
  `purchase_item_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pqrs_requester` (`requester_id`),
  KEY `idx_pqrs_status` (`status`),
  KEY `idx_pqrs_assigned_admin` (`assigned_admin_id`),
  KEY `FKc5p90b6ofq1ytby5ul6126787` (`purchase_item_id`),
  CONSTRAINT `FKc5p90b6ofq1ytby5ul6126787` FOREIGN KEY (`purchase_item_id`) REFERENCES `purchase_items` (`id`),
  CONSTRAINT `FKpjlg4vqgwhvoocl20xe6ubk3u` FOREIGN KEY (`assigned_admin_id`) REFERENCES `admin_details` (`user_id`),
  CONSTRAINT `FKqmcc04xyjwxeqbcksg91xe2tg` FOREIGN KEY (`requester_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pqrs_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `media_type` enum('AUDIO','DOCUMENT','IMAGE','MODEL','VIDEO') DEFAULT NULL,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `original_file_name` varchar(255) DEFAULT NULL,
  `owner_user_id` bigint NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `pqrs_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfm24xrvblfemid9kepqbvgam3` (`object_key`),
  KEY `idx_pqrs_assets_pqrs` (`pqrs_id`),
  KEY `idx_pqrs_assets_owner` (`owner_user_id`),
  CONSTRAINT `FK5ycwmlvu260va1ovv6navg5h0` FOREIGN KEY (`pqrs_id`) REFERENCES `pqrs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pricing_configs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `active` bit(1) NOT NULL,
  `amount_in_cents` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(255) NOT NULL,
  `description` varchar(255) NOT NULL,
  `type` enum('AD_COST_PER_SECOND_CENTS','GAME_COST_PER_POINT_CENTS','GAME_COST_PER_VICTORY_CENTS','SURVEY_REWARD_PER_QUESTION_CENTS') DEFAULT NULL,
  `version` int DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `prize_image_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `prize_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKs60h0hvq4letf203v5lvba7he` (`object_key`),
  UNIQUE KEY `UKjywfr9faxcr2vlblrntwplek2` (`prize_id`),
  CONSTRAINT `FK3elfo1nu2dc0u1oqg79vumkpo` FOREIGN KEY (`prize_id`) REFERENCES `raffle_prizes` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `is_active` bit(1) NOT NULL,
  `name` varchar(100) NOT NULL,
  `created_by_user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9qvug0bmpkmxkkx33q51m7do7` (`name`),
  KEY `FK9tphkqdei4tfajle0jx75eswr` (`created_by_user_id`),
  CONSTRAINT `FK9tphkqdei4tfajle0jx75eswr` FOREIGN KEY (`created_by_user_id`) REFERENCES `admin_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category_image_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `product_category_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5qif9b3gc9boqbd0p6fmwgosj` (`object_key`),
  UNIQUE KEY `UKgiv6tlu1treynp1kqy6t6gqv9` (`product_category_id`),
  CONSTRAINT `FKpau6xvtl8bvou5jm5fc9jscoj` FOREIGN KEY (`product_category_id`) REFERENCES `product_category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_image_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `product_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKh9nfcpdeo2ik5vqhs4ts39ae9` (`object_key`),
  UNIQUE KEY `UK2gxm8au1hgbtmpa4xkg2045k2` (`product_id`),
  CONSTRAINT `FK6wrser3lu46wkpj2gbenqlski` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_reviews` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment` text NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `rating` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `visible` bit(1) NOT NULL,
  `consumer_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `purchase_item_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_one_review_per_consumer_product` (`consumer_id`,`product_id`),
  UNIQUE KEY `UKd9p4m9xh7oanx4ra8g2p6xudb` (`purchase_item_id`),
  KEY `idx_consumer_id` (`consumer_id`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_rating` (`rating`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `FK35kxxqe2g9r4mww80w9e3tnw9` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKg5fl2mruvorljvsrqinwiou9f` FOREIGN KEY (`purchase_item_id`) REFERENCES `purchase_items` (`id`),
  CONSTRAINT `FKsgouu904eqflitq9o92t00fs4` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_stock` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(500) NOT NULL,
  `code_hash` varchar(64) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expiration_date` datetime(6) DEFAULT NULL,
  `sold_at` datetime(6) DEFAULT NULL,
  `status` enum('AVAILABLE','EXPIRED','INVALID','RESERVED','SOLD') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint DEFAULT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code_hash_per_product` (`product_id`,`code_hash`),
  KEY `idx_product_id` (`product_id`),
  KEY `idx_status` (`status`),
  KEY `idx_expiration_date_status` (`expiration_date`,`status`),
  CONSTRAINT `FKpg19826xp8orgacfm7s1lol6v` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `products` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `average_rate` double DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `deleted_by` bigint DEFAULT NULL,
  `deletion_reason` text,
  `description` text,
  `game_reward_auto_disabled` bit(1) DEFAULT NULL,
  `is_game_reward` bit(1) DEFAULT NULL,
  `max_keys_pct` int NOT NULL,
  `name` varchar(255) NOT NULL,
  `price_cents` bigint NOT NULL,
  `rejected_at` datetime(6) DEFAULT NULL,
  `rejected_until` datetime(6) DEFAULT NULL,
  `rejection_reason` text,
  `resubmission_count` int DEFAULT NULL,
  `resubmitted_at` datetime(6) DEFAULT NULL,
  `review_count` int DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE','PENDING','REJECTED') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `approved_by` bigint DEFAULT NULL,
  `commercial_id` bigint NOT NULL,
  `product_category_id` bigint NOT NULL,
  `rejected_by` bigint DEFAULT NULL,
  `target_audience_id` bigint DEFAULT NULL,
  `product_type` enum('DIGITAL','PHYSICAL') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_commercial_id` (`commercial_id`),
  KEY `idx_product_category_id` (`product_category_id`),
  KEY `idx_price` (`price_cents`),
  KEY `idx_average_rate` (`average_rate`),
  KEY `idx_status` (`status`),
  KEY `FKik558y412krauovq82uijxm86` (`approved_by`),
  KEY `FKb6fkchn4hsdskwvoa0qexy21s` (`rejected_by`),
  KEY `FKoq7qk9hfsmel2uoa9njbet20m` (`target_audience_id`),
  CONSTRAINT `FKb6fkchn4hsdskwvoa0qexy21s` FOREIGN KEY (`rejected_by`) REFERENCES `admin_details` (`user_id`),
  CONSTRAINT `FKb74tk5jq8jwurq34dks9agydq` FOREIGN KEY (`product_category_id`) REFERENCES `product_category` (`id`),
  CONSTRAINT `FKik4xcriwolohjb6wmkq9edas8` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `FKik558y412krauovq82uijxm86` FOREIGN KEY (`approved_by`) REFERENCES `admin_details` (`user_id`),
  CONSTRAINT `FKoq7qk9hfsmel2uoa9njbet20m` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_item_cash_refunds` (
  `id` binary(16) NOT NULL,
  `account_holder_doc` varchar(20) DEFAULT NULL,
  `account_holder_doc_type` enum('CC','CE','NIT','PP','TI') DEFAULT NULL,
  `account_holder_name` varchar(200) DEFAULT NULL,
  `account_number` varchar(30) DEFAULT NULL,
  `account_type` enum('CHECKING','SAVINGS') DEFAULT NULL,
  `amount_cents` bigint NOT NULL,
  `bank_details_submitted_at` datetime(6) DEFAULT NULL,
  `bank_name` varchar(100) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `status` enum('PAID','PENDING_PAYMENT') NOT NULL,
  `paid_by_admin_id` bigint DEFAULT NULL,
  `pqrs_id` bigint DEFAULT NULL,
  `purchase_item_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9wcebkdgebc1iau7mpqjmqevk` (`purchase_item_id`),
  KEY `FKkecuewdhlfnpqtv8s2jmve7ey` (`paid_by_admin_id`),
  KEY `FKmulyglwo3c287eyu513q5gdgr` (`pqrs_id`),
  CONSTRAINT `FKkecuewdhlfnpqtv8s2jmve7ey` FOREIGN KEY (`paid_by_admin_id`) REFERENCES `admin_details` (`user_id`),
  CONSTRAINT `FKlw22kwngmo89ndrofb8vejbo1` FOREIGN KEY (`purchase_item_id`) REFERENCES `purchase_items` (`id`),
  CONSTRAINT `FKmulyglwo3c287eyu513q5gdgr` FOREIGN KEY (`pqrs_id`) REFERENCES `pqrs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `commercial_id` bigint DEFAULT NULL,
  `commission_cents` bigint NOT NULL,
  `commission_pct_applied` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `delivered_code` text,
  `max_keys_pct_at_purchase` int NOT NULL,
  `net_to_commercial_cents` bigint NOT NULL,
  `product_name_snapshot` varchar(255) DEFAULT NULL,
  `status` enum('CANCELLED','CLAIMED','EXPIRED_UNCLAIMED','IN_REVIEW','PENDING','REFUNDED') NOT NULL,
  `subtotal_cents` bigint NOT NULL,
  `unit_price_cents` bigint NOT NULL,
  `product_stock_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `purchase_id` bigint NOT NULL,
  `claim_attempts` int NOT NULL,
  `claim_expires_at` datetime(6) DEFAULT NULL,
  `claim_pin_hash` varchar(100) DEFAULT NULL,
  `claimed_at` datetime(6) DEFAULT NULL,
  `status_before_review` enum('CANCELLED','CLAIMED','EXPIRED_UNCLAIMED','IN_REVIEW','PENDING','REFUNDED') DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKn90avfw59ofp0d0ymx3mnqdag` (`product_stock_id`),
  KEY `idx_purchase_items_purchase_id` (`purchase_id`),
  KEY `idx_purchase_items_product_id` (`product_id`),
  KEY `idx_purchase_items_delivered_at` (`delivered_at`),
  CONSTRAINT `FKbwtjp8gfcre77l1mxverb6up0` FOREIGN KEY (`product_id`) REFERENCES `products` (`id`),
  CONSTRAINT `FKhcski0jcuja0o3vhb7o15yqvi` FOREIGN KEY (`purchase_id`) REFERENCES `purchases` (`id`),
  CONSTRAINT `FKlsff51w4lmy1j8jpjqa4f5rjg` FOREIGN KEY (`product_stock_id`) REFERENCES `product_stock` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchases` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `cash_cents` bigint NOT NULL,
  `commission_cents` bigint NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `delivery_email` varchar(255) DEFAULT NULL,
  `delivery_email_verified` bit(1) NOT NULL,
  `keys_value_cents` bigint NOT NULL,
  `net_to_commercials_cents` bigint NOT NULL,
  `reference_id` varchar(255) NOT NULL,
  `status` enum('COMPLETED','FAILED','PENDING','PROCESSING') NOT NULL,
  `total_cents` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `consumer_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK9mkaq6wd8qopuvmjaai9jruh2` (`reference_id`),
  KEY `idx_consumer_id` (`consumer_id`),
  KEY `idx_purchase_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `FK6yqfrg19y8igj53qe3t7vxqi1` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `question_options` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_index` int NOT NULL,
  `text` varchar(300) NOT NULL,
  `question_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfxdch60fujxafuoxfbt8evraq` (`question_id`),
  CONSTRAINT `FKfxdch60fujxafuoxfbt8evraq` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`),
  CONSTRAINT `question_options_chk_1` CHECK ((`order_index` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffle_image_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('ANALYZING','ATTACHED','CANCELLED','DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `raffle_id` bigint DEFAULT NULL,
  `raffle_data_snapshot` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5ld6upb1c4ddkptclp0s7jii3` (`object_key`),
  UNIQUE KEY `UKo1sl11g2ak72awoigd30qscs7` (`raffle_id`),
  CONSTRAINT `FKa3b4kgh2t1yygg5kg1d8j5no7` FOREIGN KEY (`raffle_id`) REFERENCES `raffles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffle_participations` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `first_participation_at` datetime(6) DEFAULT NULL,
  `last_participation_at` datetime(6) DEFAULT NULL,
  `tickets_count` bigint DEFAULT NULL,
  `consumer_id` bigint DEFAULT NULL,
  `raffle_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_participation` (`raffle_id`,`consumer_id`),
  KEY `idx_raffle_participants` (`raffle_id`,`consumer_id`),
  KEY `FKevdorlgt4t8bn92khsofy0jxq` (`consumer_id`),
  CONSTRAINT `FKevdorlgt4t8bn92khsofy0jxq` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FKns8b6fjyrto67xc9xppqw5msd` FOREIGN KEY (`raffle_id`) REFERENCES `raffles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffle_prizes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `brand` varchar(100) DEFAULT NULL,
  `claim_code` varchar(255) NOT NULL,
  `claim_instructions` text,
  `claimed_count` int DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` text,
  `position` int NOT NULL,
  `prize_status` enum('DELIVERED','EXPIRED','PENDING') NOT NULL,
  `prize_type` enum('DIGITAL','PHYSICAL') DEFAULT NULL,
  `quantity` int NOT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `value` decimal(10,2) NOT NULL,
  `raffle_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_raffle_prizes` (`raffle_id`),
  KEY `idx_prize_position` (`raffle_id`,`position`),
  KEY `idx_prize_status` (`prize_status`),
  CONSTRAINT `FKr6bjsmhq4ur8du4sqq83ef7n5` FOREIGN KEY (`raffle_id`) REFERENCES `raffles` (`id`),
  CONSTRAINT `raffle_prizes_chk_1` CHECK ((`position` >= 1)),
  CONSTRAINT `raffle_prizes_chk_2` CHECK ((`quantity` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffle_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `draw_proof` text,
  `drawn_at` datetime(6) NOT NULL,
  `external_reference` varchar(255) DEFAULT NULL,
  `raffle_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8v5dpaph83c03blfd31c7hc61` (`raffle_id`),
  CONSTRAINT `FK68sen1yur4ud4c2giw45n7ed7` FOREIGN KEY (`raffle_id`) REFERENCES `raffles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffle_rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `current_tickets_by_source` bigint DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `max_tickets_by_source` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `raffle_id` bigint NOT NULL,
  `rule_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_raffle_rule` (`raffle_id`,`rule_id`),
  KEY `idx_config_raffle_rule` (`raffle_id`,`rule_id`),
  KEY `idx_config_active` (`raffle_id`,`is_active`),
  KEY `idx_config_raffle` (`raffle_id`),
  KEY `idx_config_rule` (`rule_id`),
  CONSTRAINT `FKd46hlnbyb48bx2o4gvrrhj8bi` FOREIGN KEY (`raffle_id`) REFERENCES `raffles` (`id`),
  CONSTRAINT `FKickk4x59ve3xsy08macuarj5` FOREIGN KEY (`rule_id`) REFERENCES `ticket_earning_rules` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffle_tickets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_winner` bit(1) DEFAULT NULL,
  `issued_at` datetime(6) DEFAULT NULL,
  `source` enum('DAILY_LOGIN','PURCHASE','REFERRAL') NOT NULL,
  `source_id` bigint NOT NULL,
  `status` enum('ACTIVE','CANCELLED','EXPIRED') NOT NULL,
  `ticket_number` varchar(50) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `raffle_id` bigint DEFAULT NULL,
  `ticket_owner_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_ticket_number_per_raffle` (`raffle_id`,`ticket_number`),
  KEY `idx_raffle_consumer` (`raffle_id`,`ticket_owner_id`),
  KEY `idx_consumer_tickets` (`ticket_owner_id`,`status`),
  KEY `idx_ticket_source_lookup` (`ticket_owner_id`,`source`,`source_id`),
  CONSTRAINT `FK6eu5c9ygh6clhw8hh3e2mqeli` FOREIGN KEY (`ticket_owner_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FKlnb0dfi6d6wi0d0lccw4ri5dw` FOREIGN KEY (`raffle_id`) REFERENCES `raffles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffle_winners` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `claim_deadline` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `prize_claimed` bit(1) DEFAULT NULL,
  `prize_claimed_at` datetime(6) DEFAULT NULL,
  `prize_tracking_info` varchar(255) DEFAULT NULL,
  `prize_id` bigint NOT NULL,
  `raffle_result_id` bigint NOT NULL,
  `winner_consumer_id` bigint NOT NULL,
  `winning_ticket_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKlbmfa8xtx8qc3kimfydet8tw3` (`prize_id`),
  UNIQUE KEY `UK67dutmunysp2suibtcmlekeuu` (`winning_ticket_id`),
  KEY `idx_raffle_winners` (`raffle_result_id`),
  KEY `idx_consumer_wins` (`winner_consumer_id`),
  CONSTRAINT `FK1w52y9syj60kb5xo0b478au4n` FOREIGN KEY (`prize_id`) REFERENCES `raffle_prizes` (`id`),
  CONSTRAINT `FKal4xa63torxn2sx624es6glao` FOREIGN KEY (`winner_consumer_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FKgv8837ejf8jalwktyx8dtyd1` FOREIGN KEY (`winning_ticket_id`) REFERENCES `raffle_tickets` (`id`),
  CONSTRAINT `FKl1ve4vp6a998t8u8njqb3cmgk` FOREIGN KEY (`raffle_result_id`) REFERENCES `raffle_results` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `raffles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `description` text,
  `draw_date` datetime(6) NOT NULL,
  `draw_method` enum('RANDOM_ORG','SYSTEM_RANDOM') NOT NULL,
  `end_date` datetime(6) NOT NULL,
  `max_tickets_per_user` int DEFAULT NULL,
  `max_total_tickets` int DEFAULT NULL,
  `modified_by` bigint DEFAULT NULL,
  `raffle_status` enum('DRAFT','ACTIVE','CLOSED','DRAWING','LIVE','COMPLETED','CANCELLED','MISSED_DRAW') NOT NULL,
  `raffle_type` enum('PREMIUM','STANDARD') NOT NULL,
  `requires_pet` bit(1) NOT NULL,
  `start_date` datetime(6) NOT NULL,
  `terms_and_conditions` text,
  `title` varchar(200) NOT NULL,
  `total_participants` int DEFAULT NULL,
  `total_tickets_issued` int DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `target_audience_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`raffle_status`),
  KEY `idx_type` (`raffle_type`),
  KEY `idx_dates` (`start_date`,`end_date`),
  KEY `FKekwm6retp7ayio21ve7oa5ps` (`target_audience_id`),
  CONSTRAINT `FKekwm6retp7ayio21ve7oa5ps` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `reactivation_mission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed` bit(1) NOT NULL,
  `consumer_id` bigint NOT NULL,
  `expired` bit(1) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `xp_goal` bigint NOT NULL,
  `xp_progress` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_tokens` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `jti` varchar(100) NOT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  `revoked` bit(1) NOT NULL,
  `token` varchar(1024) NOT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `username` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK18kw8ppjw4gmmgdns9ecyg17b` (`jti`),
  KEY `idx_rt_username` (`username`),
  KEY `idx_rt_jti` (`jti`),
  KEY `idx_rt_ip` (`ip_address`),
  KEY `idx_rt_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `screening_results` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `officer_notes` varchar(500) DEFAULT NULL,
  `queried_document` varchar(30) DEFAULT NULL,
  `queried_name` varchar(300) NOT NULL,
  `raw_response` text,
  `reference_id` varchar(100) DEFAULT NULL,
  `restrictive_list` enum('ATTORNEY_GENERAL','COMPTROLLER','NATIONAL_POLICE','OFAC_SDN','UN') NOT NULL,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `reviewed_by_officer_id` bigint DEFAULT NULL,
  `status` enum('FUZZY_HIT','HIT','NO_HIT') NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_screening_user_id` (`user_id`),
  KEY `idx_screening_status` (`status`),
  KEY `idx_screening_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `story_media_assets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `alt_text` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `display_order` int DEFAULT NULL,
  `file_name` varchar(255) DEFAULT NULL,
  `is_cover` bit(1) DEFAULT NULL,
  `media_type` enum('AUDIO','DOCUMENT','IMAGE','MODEL','VIDEO') NOT NULL,
  `mime_type` enum('APPLICATION_PDF','AUDIO_MP3','AUDIO_MPEG','AUDIO_OGG','AUDIO_WAV','IMAGE_JPEG','IMAGE_JPG','IMAGE_PNG','IMAGE_WEBP','VIDEO_MP4','VIDEO_QUICK_TIME') DEFAULT NULL,
  `object_key` varchar(500) NOT NULL,
  `public_url` varchar(500) DEFAULT NULL,
  `size_bytes` bigint NOT NULL,
  `status` enum('DELETED','ORPHANED','PENDING','VALIDATED') NOT NULL,
  `thumbnail_url` varchar(500) DEFAULT NULL,
  `impact_story_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKfya762v1a0uth497ajwtpd8xa` (`object_key`),
  KEY `FK3o3q2793t0vffgn0wfm31f7t6` (`impact_story_id`),
  CONSTRAINT `FK3o3q2793t0vffgn0wfm31f7t6` FOREIGN KEY (`impact_story_id`) REFERENCES `impact_stories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscriptions` (
  `id` binary(16) NOT NULL,
  `amount_paid_cents` bigint NOT NULL,
  `cancellation_reason` varchar(500) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `renewal_reminder_sent_at` datetime(6) DEFAULT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','CANCELLED','EXPIRED','PAYMENT_FAILED','PENDING_PAYMENT','RENEWED') NOT NULL,
  `terminated_at` datetime(6) DEFAULT NULL,
  `wompi_reference` varchar(100) DEFAULT NULL,
  `commercial_id` bigint NOT NULL,
  `plan_id` bigint NOT NULL,
  `wompi_transaction_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKmmyyw9hbbmv3qosv5x8oqh6k5` (`wompi_reference`),
  UNIQUE KEY `UKpw3s13m9th1elw9miqas7whvn` (`wompi_transaction_id`),
  KEY `idx_sub_commercial_id` (`commercial_id`),
  KEY `idx_sub_status` (`status`),
  KEY `idx_sub_end_date` (`end_date`),
  KEY `idx_sub_wompi_reference` (`wompi_reference`),
  KEY `FKb1uf5qnxi6uj95se8ykydntl1` (`plan_id`),
  CONSTRAINT `FK3coix01ejq146al2p77a1paq2` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `FKb1uf5qnxi6uj95se8ykydntl1` FOREIGN KEY (`plan_id`) REFERENCES `plans` (`id`),
  CONSTRAINT `FKdbrfhprxh6jdgh4dl5tw4llul` FOREIGN KEY (`wompi_transaction_id`) REFERENCES `wompi_transactions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `survey_answers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `text_answer` varchar(1000) DEFAULT NULL,
  `question_id` bigint NOT NULL,
  `selected_option_id` bigint DEFAULT NULL,
  `session_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgl5lld6jreh81eovnut8dtx5y` (`question_id`),
  KEY `FKlglvku9o9jlqi8hnccyiu83bn` (`selected_option_id`),
  KEY `FK9sc72ggwico3i9tkaax27da9a` (`session_id`),
  CONSTRAINT `FK9sc72ggwico3i9tkaax27da9a` FOREIGN KEY (`session_id`) REFERENCES `survey_sessions` (`id`),
  CONSTRAINT `FKgl5lld6jreh81eovnut8dtx5y` FOREIGN KEY (`question_id`) REFERENCES `survey_questions` (`id`),
  CONSTRAINT `FKlglvku9o9jlqi8hnccyiu83bn` FOREIGN KEY (`selected_option_id`) REFERENCES `question_options` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `survey_questions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_index` int NOT NULL,
  `is_required` bit(1) NOT NULL,
  `text` varchar(500) NOT NULL,
  `type` enum('MULTIPLE_CHOICE','RATING','SINGLE_CHOICE','TEXT','YES_NO') NOT NULL,
  `survey_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK91fvqiwu0dj35uxpnx7qpoiw6` (`survey_id`),
  CONSTRAINT `FK91fvqiwu0dj35uxpnx7qpoiw6` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`),
  CONSTRAINT `survey_questions_chk_1` CHECK ((`order_index` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `survey_rewards` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` bigint NOT NULL,
  `granted_at` datetime(6) DEFAULT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `status` enum('FAILED','PENDING','PROCESSED') NOT NULL,
  `session_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK860x9eay04qd0vwl0nlilierh` (`session_id`),
  CONSTRAINT `FKey5ykvc1jb2vv4u47esafhfbo` FOREIGN KEY (`session_id`) REFERENCES `survey_sessions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `survey_sessions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `completed_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `started_at` datetime(6) NOT NULL,
  `status` enum('ABANDONED','ACTIVE','COMPLETED','EXPIRED') NOT NULL,
  `version` bigint DEFAULT NULL,
  `consumer_id` bigint NOT NULL,
  `survey_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_session_survey_consumer` (`survey_id`,`consumer_id`),
  KEY `idx_session_status_expires` (`status`,`expires_at`),
  KEY `FK5ijiatnmspdhnh4cp1egfy7kn` (`consumer_id`),
  CONSTRAINT `FK5ijiatnmspdhnh4cp1egfy7kn` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`),
  CONSTRAINT `FKk51aayk9r7dyb4xbie6jdwk52` FOREIGN KEY (`survey_id`) REFERENCES `surveys` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `surveys` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `ends_at` datetime(6) DEFAULT NULL,
  `max_responses` int DEFAULT NULL,
  `rejection_reason` text,
  `response_count` int DEFAULT NULL,
  `reward_amount_per_question_cents` bigint NOT NULL,
  `starts_at` datetime(6) DEFAULT NULL,
  `status` enum('ACTIVE','APPROVED','COMPLETED','DRAFT','PAUSED','PENDING_REVIEW','REJECTED','SUSPENDED') NOT NULL,
  `title` varchar(200) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `creator_id` bigint NOT NULL,
  `target_audience_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKeedskddkhmubkcan02hpgtw3u` (`creator_id`),
  KEY `FKslwwkx65iu09y98ghf7vsoy6x` (`target_audience_id`),
  CONSTRAINT `FKeedskddkhmubkcan02hpgtw3u` FOREIGN KEY (`creator_id`) REFERENCES `commercial_details` (`user_id`),
  CONSTRAINT `FKslwwkx65iu09y98ghf7vsoy6x` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_features` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` enum('ADMINISTRATION','ENGAGEMENT','FINANCIAL','MARKETPLACE','MONETIZATION','USER_ACQUISITION','USER_PROFILES') NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `endpoint_prefix` varchar(255) NOT NULL,
  `feature_key` varchar(255) NOT NULL,
  `status` enum('DISABLED','ENABLED','MAINTENANCE','READ_ONLY') NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKi01i62flhc7dl9se5l4phpola` (`endpoint_prefix`),
  UNIQUE KEY `UKcia5wamet70i5cuqqop7tfbm3` (`feature_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `target_audience_categories` (
  `target_audience_id` bigint NOT NULL,
  `category_id` bigint NOT NULL,
  KEY `FK6jdo65s9jb9uqxu1liq0rfdlb` (`category_id`),
  KEY `FK3cu91em0lwilgor3nv00fcajp` (`target_audience_id`),
  CONSTRAINT `FK3cu91em0lwilgor3nv00fcajp` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`),
  CONSTRAINT `FK6jdo65s9jb9uqxu1liq0rfdlb` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `target_audience_municipalities` (
  `target_audience_id` bigint NOT NULL,
  `municipality_code` varchar(5) NOT NULL,
  KEY `FKj1vg4emcs8hkqi50159uvvtdp` (`municipality_code`),
  KEY `FKib2npc5rqpyg2crgtkq3w6s7c` (`target_audience_id`),
  CONSTRAINT `FKib2npc5rqpyg2crgtkq3w6s7c` FOREIGN KEY (`target_audience_id`) REFERENCES `target_audiences` (`id`),
  CONSTRAINT `FKj1vg4emcs8hkqi50159uvvtdp` FOREIGN KEY (`municipality_code`) REFERENCES `municipality` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `target_audiences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `max_age` int DEFAULT NULL,
  `min_age` int DEFAULT NULL,
  `target_gender` enum('ALL','FEMALE','MALE') DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ticket_audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` enum('ISSUED') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `ip_address` varchar(255) DEFAULT NULL,
  `metadata` text,
  `source_id` bigint DEFAULT NULL,
  `source_type` enum('DAILY_LOGIN','PURCHASE','REFERRAL') DEFAULT NULL,
  `ticket_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ticket_action` (`ticket_id`,`action`),
  KEY `idx_source` (`source_type`,`source_id`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `FKj7l8jyjntn6374mxdw1aj7pfi` FOREIGN KEY (`ticket_id`) REFERENCES `raffle_tickets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ticket_earning_rules` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `daily_login` bit(1) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `last_modified_by` bigint DEFAULT NULL,
  `min_purchase_amount_cents` bigint DEFAULT NULL,
  `priority` int NOT NULL,
  `referral_added_quantity` int DEFAULT NULL,
  `rule_name` varchar(100) NOT NULL,
  `rule_type` enum('DAILY_LOGIN','PURCHASE','REFERRAL') NOT NULL,
  `tickets_to_award` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK871olw07q83w1wq52036rc6r3` (`rule_name`),
  KEY `idx_active_rules` (`is_active`,`rule_type`),
  KEY `idx_rule_type` (`rule_type`),
  KEY `idx_priority` (`priority` DESC),
  CONSTRAINT `ticket_earning_rules_chk_1` CHECK ((`tickets_to_award` >= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `treasury_accounts` (
  `id` binary(16) NOT NULL,
  `balance_cents` bigint NOT NULL,
  `code` enum('EXTERNAL_INCOME','FORTIFICATION','KEYS_RESERVE','OPERATIONS','PAYOUTS_PENDING') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `name` varchar(100) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1skwc94tevd8abrsmxqvq3xr5` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `treasury_movements` (
  `id` binary(16) NOT NULL,
  `amount_cents` bigint NOT NULL,
  `concept` enum('BASIC_PLAN_SUBSCRIPTION','BUSINESS_DEPOSIT_FORTIFICATION','BUSINESS_DEPOSIT_KEYS','BUSINESS_DEPOSIT_OPERATIONS','COMMISSION_RETENTION','COMMISSION_REVERSAL','COPAYMENT_KEYS_CONVERSION','EXPIRED_KEYS_TO_FORTIFICATION','FORTIFICATION_PURCHASE','PAYOUT_TO_BUSINESS','REFUND_CASH_TO_OPERATIONS','REFUND_KEYS_TO_RESERVE','REFUND_TO_BUYER','SALE_TO_PAYOUT_PENDING') NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `reference_id` binary(16) NOT NULL,
  `reference_type` varchar(40) NOT NULL,
  `from_account_id` binary(16) NOT NULL,
  `to_account_id` binary(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKq55opny9yc1g3nxce950pfk06` (`from_account_id`),
  KEY `FKi4fjle5n90hswmwhc6lrr1tft` (`to_account_id`),
  CONSTRAINT `FKi4fjle5n90hswmwhc6lrr1tft` FOREIGN KEY (`to_account_id`) REFERENCES `treasury_accounts` (`id`),
  CONSTRAINT `FKq55opny9yc1g3nxce950pfk06` FOREIGN KEY (`from_account_id`) REFERENCES `treasury_accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_details` (
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`user_id`),
  CONSTRAINT `FKicouhgavvmiiohc28mgk0kuj5` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_level_profile` (
  `consumer_id` bigint NOT NULL,
  `benefits_paused` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_level` enum('BRONCE','DIAMANTE','ESMERALDA','ORO','PLATA','RUBI') NOT NULL,
  `last_activity_at` datetime(6) NOT NULL,
  `reactivation_mission_active` bit(1) NOT NULL,
  `xp_total` bigint NOT NULL,
  PRIMARY KEY (`consumer_id`),
  CONSTRAINT `FKmoy2fkvtepy68ujbrbxjr0b9s` FOREIGN KEY (`consumer_id`) REFERENCES `consumer_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_verification` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `document_image_url` varchar(255) DEFAULT NULL,
  `document_number` varchar(255) DEFAULT NULL,
  `document_type` varchar(255) DEFAULT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `verification_status` enum('APPROVED','PENDING','REJECTED') DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKha4odgfncscp0wbex975lioc5` (`user_id`),
  CONSTRAINT `FKtj9xd3vagu4sh7xk3ep9c2wx6` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `account_locked_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `failed_login_attempts` int NOT NULL DEFAULT '0',
  `password` varchar(255) DEFAULT NULL,
  `password_configured` tinyint(1) NOT NULL DEFAULT '1',
  `phone_number` varchar(255) NOT NULL,
  `public_id` binary(16) NOT NULL,
  `registered_date` datetime(6) DEFAULT NULL,
  `role` enum('ADMIN','COMMERCIAL','COMPLIANCE_OFFICER','CONSUMER','GAME_DESIGNER') DEFAULT NULL,
  `user_state` enum('ACTIVE','BLOCKED','PENDING_EMAIL','PENDING_KYC_REVIEW') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK9q63snka3mdh91as4io72espi` (`phone_number`),
  UNIQUE KEY `UKs24bux761rbgowsl7a4b386ba` (`public_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `balance_cents` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `exhausted_since` datetime(6) DEFAULT NULL,
  `last_budget_alert_at` datetime(6) DEFAULT NULL,
  `last_budget_alert_stage` enum('CRITICAL','DORMANT','EXHAUSTED','NONE','WARNING') NOT NULL,
  `last_deposit_amount_cents` bigint DEFAULT NULL,
  `last_updated` datetime(6) NOT NULL,
  `status` enum('ACTIVE','EXHAUSTED','INACTIVE') NOT NULL,
  `version` bigint NOT NULL,
  `commercial_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKggq27sykg6p6fbq6p09smnkbu` (`commercial_id`),
  CONSTRAINT `FK7uwufrwp1om1glcet6hcsyq9q` FOREIGN KEY (`commercial_id`) REFERENCES `commercial_details` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wompi_transactions` (
  `id` binary(16) NOT NULL,
  `amount_in_cents` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `metadata` json DEFAULT NULL,
  `reference` varchar(100) NOT NULL,
  `status` enum('APPROVED','DECLINED','ERROR','PENDING','VOIDED') NOT NULL,
  `type` enum('CHARGE_BUSINESS_DEPOSIT','CHARGE_COPAYMENT','CHARGE_PLAN_SUBSCRIPTION','TRANSFER_PAYOUT') NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `wompi_created_at` datetime(6) DEFAULT NULL,
  `wompi_id` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8jycjs085eio4jp5qui5w0d2t` (`reference`),
  UNIQUE KEY `UK4r4ycht1pbms2kiti4l5sr5g2` (`wompi_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `xp_key_transaction_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_type` enum('GAME_PLAYED','PURCHASE','REFERRAL_ACTIVE','SURVEY_COMPLETED','VIDEO_WATCHED') NOT NULL,
  `consumer_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `multiplier_applied` double NOT NULL,
  `xp_earned` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_xplog_consumer_created` (`consumer_id`,`created_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

