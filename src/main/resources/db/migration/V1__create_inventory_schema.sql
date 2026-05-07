CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    total_stock BIGINT NOT NULL,
    available_stock BIGINT NOT NULL,
    reserved_stock BIGINT NOT NULL DEFAULT 0,
    sold_stock BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_products_total_non_negative CHECK (total_stock >= 0),
    CONSTRAINT chk_products_available_non_negative CHECK (available_stock >= 0),
    CONSTRAINT chk_products_reserved_non_negative CHECK (reserved_stock >= 0),
    CONSTRAINT chk_products_sold_non_negative CHECK (sold_stock >= 0),
    CONSTRAINT chk_products_stock_invariant CHECK (total_stock = available_stock + reserved_stock + sold_stock)
);

CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID NOT NULL UNIQUE,
    product_id UUID NOT NULL REFERENCES products(id),
    user_id VARCHAR(128) NOT NULL,
    quantity BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_reservations_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_reservations_status CHECK (status IN ('ACTIVE', 'CONFIRMED', 'RELEASED', 'EXPIRED'))
);

CREATE INDEX idx_reservations_product_id ON reservations(product_id);
CREATE INDEX idx_reservations_active_expiry ON reservations(expires_at) WHERE status = 'ACTIVE';

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE,
    reservation_id UUID NOT NULL UNIQUE REFERENCES reservations(reservation_id),
    product_id UUID NOT NULL REFERENCES products(id),
    user_id VARCHAR(128) NOT NULL,
    quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0)
);
