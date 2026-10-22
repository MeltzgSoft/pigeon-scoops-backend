ALTER TABLE grocery_unit
    ALTER COLUMN unit_cost TYPE DOUBLE PRECISION USING unit_cost::text::double precision,
    ALTER COLUMN unit_mass TYPE DOUBLE PRECISION USING unit_mass::text::double precision,
    ALTER COLUMN unit_volume TYPE DOUBLE PRECISION USING unit_volume::text::double precision,
    ALTER COLUMN unit_common TYPE DOUBLE PRECISION USING unit_common::text::double precision;

ALTER TABLE recipe
    ALTER COLUMN amount TYPE DOUBLE PRECISION USING amount::text::double precision;

ALTER TABLE ingredient
    ALTER COLUMN amount TYPE DOUBLE PRECISION USING amount::text::double precision;

ALTER TABLE order_item
    ALTER COLUMN amount TYPE DOUBLE PRECISION USING amount::text::double precision;

ALTER TABLE menu_item_size
    ALTER COLUMN amount TYPE DOUBLE PRECISION USING amount::text::double precision;

