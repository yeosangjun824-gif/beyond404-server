CREATE TABLE appliance_specs (
    spec_id         BIGSERIAL PRIMARY KEY,
    brand           VARCHAR(50)  NOT NULL DEFAULT 'LG',
    appliance_type  VARCHAR(30)  NOT NULL,
    model_name      VARCHAR(100) NOT NULL,
    weight_kg       DECIMAL(6,2),
    capacity_l      DECIMAL(8,1),
    capacity_kg     DECIMAL(6,1),
    screen_inch     DECIMAL(5,1),
    size_grade      VARCHAR(10),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_appliance_specs_model_name ON appliance_specs(LOWER(model_name));
CREATE INDEX idx_appliance_specs_appliance_type ON appliance_specs(appliance_type);
