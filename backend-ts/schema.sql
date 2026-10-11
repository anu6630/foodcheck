-- FoodCheck schema (Neon Postgres)

CREATE TABLE IF NOT EXISTS countries (
  code VARCHAR(5) PRIMARY KEY,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS ingredients (
  id VARCHAR(100) PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  category VARCHAR(100),
  e_number VARCHAR(20),
  inci_name VARCHAR(255),
  cas_number VARCHAR(50)
);

CREATE INDEX IF NOT EXISTS idx_ingredients_e_number ON ingredients (e_number);
CREATE INDEX IF NOT EXISTS idx_ingredients_name_lower ON ingredients (LOWER(name));

CREATE TABLE IF NOT EXISTS ingredient_names (
  id SERIAL PRIMARY KEY,
  ingredient_id VARCHAR(100) NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  lang VARCHAR(5) NOT NULL,
  name VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingredient_names_name ON ingredient_names (name);
CREATE INDEX IF NOT EXISTS idx_ingredient_names_name_lower ON ingredient_names (LOWER(name));

-- Numbering schemes are not a 1:1 legal identity (INS ≈ E by Codex convention).
-- One ingredient row; many codes. Future systems (FDC, JECFA, CN) are extra rows.
CREATE TABLE IF NOT EXISTS ingredient_codes (
  id SERIAL PRIMARY KEY,
  ingredient_id VARCHAR(160) NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  system VARCHAR(32) NOT NULL,
  code VARCHAR(40) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ingredient_codes_ing_sys_code
  ON ingredient_codes (ingredient_id, system, LOWER(code));
CREATE INDEX IF NOT EXISTS idx_ingredient_codes_lookup
  ON ingredient_codes (system, LOWER(code));

CREATE TABLE IF NOT EXISTS ingredient_bans (
  id SERIAL PRIMARY KEY,
  ingredient_id VARCHAR(100) NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  country_code VARCHAR(5) NOT NULL REFERENCES countries(code) ON DELETE CASCADE,
  status VARCHAR(20) NOT NULL,
  condition VARCHAR(500),
  regulation_ref VARCHAR(255),
  effective_date DATE,
  source_id VARCHAR(80)
);

-- Per-food-category caps (FSSAI Appendix A: this additive, this food, this mg/kg).
-- ingredient_bans stays one row per country (overall status); this table holds the provisions.
CREATE TABLE IF NOT EXISTS ingredient_use_limits (
  id SERIAL PRIMARY KEY,
  ingredient_id VARCHAR(160) NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  country_code VARCHAR(5) NOT NULL REFERENCES countries(code) ON DELETE CASCADE,
  food_category_code VARCHAR(64) NOT NULL DEFAULT '',
  food_category_name VARCHAR(255),
  max_level VARCHAR(80) NOT NULL DEFAULT '',
  note TEXT,
  regulation_ref VARCHAR(255),
  source_id VARCHAR(80)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ingredient_use_limits_ing_cc_cat_lvl
  ON ingredient_use_limits (ingredient_id, country_code, food_category_code, max_level);
CREATE INDEX IF NOT EXISTS idx_ingredient_use_limits_ing
  ON ingredient_use_limits (ingredient_id);

-- Multi-source health claims (IARC, OpenFoodTox ADI/genotox, JECFA, EU withdrawn, …).
-- Do not unique on kind alone — same topic from two publishers stays two rows.
CREATE TABLE IF NOT EXISTS ingredient_health_reports (
  id SERIAL PRIMARY KEY,
  ingredient_id VARCHAR(160) NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  source_id VARCHAR(80) NOT NULL,
  kind VARCHAR(40) NOT NULL,
  summary TEXT NOT NULL,
  value TEXT,
  unit TEXT,
  evaluation_year VARCHAR(20),
  citation TEXT,
  citation_url TEXT,
  payload JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ingredient_health_reports_ing
  ON ingredient_health_reports (ingredient_id);
CREATE INDEX IF NOT EXISTS idx_ingredient_health_reports_source
  ON ingredient_health_reports (source_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_ingredient_health_reports_claim
  ON ingredient_health_reports (
    ingredient_id, source_id, kind, COALESCE(value, ''), COALESCE(citation_url, '')
  );

-- Captured datasets (PDF/CSV/API). Current pointer in data_sources; every ingest
-- also appends data_source_snapshots so a newer FSSAI PDF cannot erase 04.11.2024.
CREATE TABLE IF NOT EXISTS data_sources (
  id VARCHAR(80) PRIMARY KEY,
  publisher VARCHAR(120) NOT NULL,
  country_code VARCHAR(5) REFERENCES countries(code),
  title VARCHAR(255) NOT NULL,
  version VARCHAR(80),
  version_date DATE,
  citation TEXT,
  citation_url TEXT,
  url TEXT,
  urls JSONB,
  file_path TEXT,
  r2_key TEXT,
  file_sha256 VARCHAR(64),
  file_bytes BIGINT,
  content_type TEXT,
  retrieved_at TIMESTAMPTZ NOT NULL,
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  notes TEXT,
  stats JSONB
);
CREATE TABLE IF NOT EXISTS data_source_snapshots (
  id SERIAL PRIMARY KEY,
  source_id VARCHAR(80) NOT NULL REFERENCES data_sources(id) ON DELETE CASCADE,
  publisher VARCHAR(120) NOT NULL,
  country_code VARCHAR(5),
  title VARCHAR(255) NOT NULL,
  version VARCHAR(80),
  version_date DATE,
  citation TEXT,
  citation_url TEXT,
  url TEXT,
  urls JSONB,
  file_path TEXT,
  r2_key TEXT,
  file_sha256 VARCHAR(64),
  file_bytes BIGINT,
  content_type TEXT,
  retrieved_at TIMESTAMPTZ NOT NULL,
  ingested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  notes TEXT,
  stats JSONB
);
CREATE TABLE IF NOT EXISTS data_source_files (
  id SERIAL PRIMARY KEY,
  source_id VARCHAR(80) NOT NULL REFERENCES data_sources(id) ON DELETE CASCADE,
  snapshot_id INTEGER REFERENCES data_source_snapshots(id) ON DELETE SET NULL,
  kind VARCHAR(40) NOT NULL DEFAULT 'original',
  filename TEXT NOT NULL,
  content_type TEXT,
  bytes BIGINT,
  sha256 VARCHAR(64) NOT NULL,
  r2_key TEXT NOT NULL,
  collected_from_url TEXT,
  archived_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_data_source_files_source_sha
  ON data_source_files (source_id, sha256);
CREATE INDEX IF NOT EXISTS idx_data_source_files_source
  ON data_source_files (source_id);

CREATE TABLE IF NOT EXISTS products (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  brand VARCHAR(255),
  barcode VARCHAR(50) NOT NULL UNIQUE,
  category VARCHAR(100),
  source VARCHAR(50) NOT NULL,
  published BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_products_barcode ON products (barcode);
CREATE INDEX IF NOT EXISTS idx_products_name_lower ON products (LOWER(name));

CREATE TABLE IF NOT EXISTS product_ingredients (
  id SERIAL PRIMARY KEY,
  product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
  ingredient_id VARCHAR(100) NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
  "order" INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS product_requests (
  id SERIAL PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  product_name VARCHAR(255) NOT NULL,
  brand VARCHAR(255),
  barcode VARCHAR(50),
  category VARCHAR(100),
  country_code VARCHAR(5) NOT NULL REFERENCES countries(code),
  ingredients_text TEXT,
  requester_notes TEXT,
  image_1_key TEXT,
  image_2_key TEXT,
  image_3_key TEXT
);

CREATE TABLE IF NOT EXISTS ingredient_requests (
  id SERIAL PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  status VARCHAR(20) NOT NULL DEFAULT 'pending',
  ingredient_name VARCHAR(255) NOT NULL,
  e_number VARCHAR(20),
  category VARCHAR(100),
  requester_notes TEXT
);

CREATE TABLE IF NOT EXISTS app_users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  device_key VARCHAR(80) NOT NULL UNIQUE,
  phone VARCHAR(32) UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scans (
  id SERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  ingredients_text TEXT,
  snapshot JSONB NOT NULL DEFAULT '[]',
  image_key TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS companies (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS brand_names (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  company_id INTEGER REFERENCES companies(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS shelf_items (
  id SERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  scan_id INTEGER NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
  display_name VARCHAR(255) NOT NULL,
  brand VARCHAR(255),
  brand_id INTEGER REFERENCES brand_names(id) ON DELETE SET NULL,
  barcode VARCHAR(50),
  stance VARCHAR(20) NOT NULL DEFAULT 'unsure',
  tags JSONB NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS shelf_item_companies (
  shelf_item_id INTEGER NOT NULL REFERENCES shelf_items(id) ON DELETE CASCADE,
  company_id INTEGER NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
  PRIMARY KEY (shelf_item_id, company_id)
);

CREATE TABLE IF NOT EXISTS stories (
  id SERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  scan_id INTEGER NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
  caption TEXT,
  snapshot JSONB NOT NULL DEFAULT '[]',
  published_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS story_comments (
  id SERIAL PRIMARY KEY,
  story_id INTEGER NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS story_likes (
  story_id INTEGER NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (story_id, user_id)
);

-- Side-by-side comparison of 2–3 packs. Each item points at its own scans row
-- (new label read, or reused from My list). Brand and name are user input.
CREATE TABLE IF NOT EXISTS comparisons (
  id SERIAL PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
  category VARCHAR(60),
  tags JSONB NOT NULL DEFAULT '[]',
  decision VARCHAR(12) NOT NULL DEFAULT 'undecided',
  chosen_slot SMALLINT,
  reasons JSONB NOT NULL DEFAULT '[]',
  note TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_comparisons_user_created ON comparisons (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS comparison_items (
  comparison_id INTEGER NOT NULL REFERENCES comparisons(id) ON DELETE CASCADE,
  slot SMALLINT NOT NULL,
  scan_id INTEGER NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
  display_name VARCHAR(255) NOT NULL,
  brand VARCHAR(255),
  front_image_key TEXT,
  flagged_count INTEGER NOT NULL DEFAULT 0,
  ingredient_count INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (comparison_id, slot)
);
CREATE INDEX IF NOT EXISTS idx_comparison_items_scan ON comparison_items (scan_id);
