CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    thinq_user_key VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    phone_number VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE swap_requests (
    swap_request_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    appliance_type VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    request_channel VARCHAR(30) NOT NULL DEFAULT 'THINQ_APP',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    cancelled_at TIMESTAMPTZ
);

CREATE TABLE appliances (
    appliance_id BIGSERIAL PRIMARY KEY,
    swap_request_id BIGINT NOT NULL UNIQUE REFERENCES swap_requests(swap_request_id) ON DELETE CASCADE,
    appliance_type VARCHAR(30) NOT NULL,
    brand VARCHAR(50),
    model_name VARCHAR(100),
    estimated_age VARCHAR(30),
    exterior_condition VARCHAR(50),
    confirmed_by_customer BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE appliance_images (
    appliance_image_id BIGSERIAL PRIMARY KEY,
    swap_request_id BIGINT NOT NULL REFERENCES swap_requests(swap_request_id) ON DELETE CASCADE,
    appliance_id BIGINT REFERENCES appliances(appliance_id) ON DELETE SET NULL,
    image_type VARCHAR(30) NOT NULL DEFAULT 'CUSTOMER_CAPTURE',
    file_name VARCHAR(255) NOT NULL,
    image_url TEXT,
    storage_key VARCHAR(255),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE valuations (
    valuation_id BIGSERIAL PRIMARY KEY,
    swap_request_id BIGINT NOT NULL REFERENCES swap_requests(swap_request_id) ON DELETE CASCADE,
    valuation_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    min_amount INTEGER,
    max_amount INTEGER,
    final_amount INTEGER,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    summary_reason TEXT,
    exterior_reason TEXT,
    parts_reason TEXT,
    material_reason TEXT,
    processing_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ
);

CREATE TABLE pickup_requests (
    pickup_request_id BIGSERIAL PRIMARY KEY,
    swap_request_id BIGINT NOT NULL REFERENCES swap_requests(swap_request_id) ON DELETE CASCADE,
    crew_id BIGINT,
    pickup_type VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'REQUESTED',
    booking_date DATE,
    booking_time VARCHAR(20),
    address VARCHAR(255) NOT NULL,
    detail_address VARCHAR(255),
    pickup_lat NUMERIC(10, 7),
    pickup_lng NUMERIC(10, 7),
    eta_minutes INTEGER,
    crew_name VARCHAR(80),
    crew_phone VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    matched_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE TABLE tracking_events (
    tracking_event_id BIGSERIAL PRIMARY KEY,
    pickup_request_id BIGINT NOT NULL REFERENCES pickup_requests(pickup_request_id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    message VARCHAR(255),
    lat NUMERIC(10, 7),
    lng NUMERIC(10, 7),
    heading NUMERIC(6, 2),
    speed NUMERIC(6, 2),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE re_reviews (
    re_review_id BIGSERIAL PRIMARY KEY,
    swap_request_id BIGINT NOT NULL REFERENCES swap_requests(swap_request_id) ON DELETE CASCADE,
    valuation_id BIGINT REFERENCES valuations(valuation_id) ON DELETE SET NULL,
    reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    result_amount INTEGER,
    result_message TEXT
);

CREATE TABLE credits (
    credit_id BIGSERIAL PRIMARY KEY,
    swap_request_id BIGINT NOT NULL UNIQUE REFERENCES swap_requests(swap_request_id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    amount INTEGER NOT NULL,
    currency VARCHAR(10) NOT NULL DEFAULT 'INR',
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ
);

CREATE TABLE market_products (
    market_product_id BIGSERIAL PRIMARY KEY,
    category VARCHAR(30) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    model_name VARCHAR(100),
    price INTEGER NOT NULL,
    image_url TEXT,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pickup_result_reports (
    pickup_result_report_id BIGSERIAL PRIMARY KEY,
    swap_request_id BIGINT NOT NULL UNIQUE REFERENCES swap_requests(swap_request_id) ON DELETE CASCADE,
    pickup_request_id BIGINT REFERENCES pickup_requests(pickup_request_id) ON DELETE SET NULL,
    appliance_id BIGINT REFERENCES appliances(appliance_id) ON DELETE SET NULL,
    process_result VARCHAR(50) NOT NULL,
    reusable_parts_summary TEXT,
    recycled_material_summary TEXT,
    environmental_impact_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    notification_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    swap_request_id BIGINT REFERENCES swap_requests(swap_request_id) ON DELETE SET NULL,
    title VARCHAR(120) NOT NULL,
    message TEXT NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UNREAD',
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_swap_requests_user_id ON swap_requests(user_id);
CREATE INDEX idx_swap_requests_status ON swap_requests(status);
CREATE INDEX idx_appliance_images_swap_request_id ON appliance_images(swap_request_id);
CREATE INDEX idx_valuations_swap_request_id ON valuations(swap_request_id);
CREATE INDEX idx_pickup_requests_swap_request_id ON pickup_requests(swap_request_id);
CREATE INDEX idx_pickup_requests_status ON pickup_requests(status);
CREATE INDEX idx_tracking_events_pickup_request_id ON tracking_events(pickup_request_id);
CREATE INDEX idx_re_reviews_swap_request_id ON re_reviews(swap_request_id);
CREATE INDEX idx_credits_user_id ON credits(user_id);
CREATE INDEX idx_notifications_user_id ON notifications(user_id);
CREATE INDEX idx_notifications_status ON notifications(status);
