CREATE TABLE product_rule (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku                VARCHAR(64)              NOT NULL,
    name               VARCHAR(120)             NOT NULL,
    margin_percentage  NUMERIC(5, 2)            NOT NULL,
    preferred_provider VARCHAR(16)              NOT NULL,
    active             BOOLEAN                  NOT NULL,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_product_rule_sku UNIQUE (sku),
    CONSTRAINT ck_product_rule_margin CHECK (margin_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_product_rule_provider CHECK (preferred_provider IN ('AUTO', 'PROVIDER_A', 'PROVIDER_B'))
);
