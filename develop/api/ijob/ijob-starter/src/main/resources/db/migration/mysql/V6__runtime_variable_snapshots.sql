ALTER TABLE ifj_schedule
    ADD COLUMN variables LONGTEXT NULL;

ALTER TABLE ifj_trigger
    ADD COLUMN variables LONGTEXT NULL;

ALTER TABLE ifj_instance
    ADD COLUMN input_variables LONGTEXT NULL,
    ADD COLUMN output_variables LONGTEXT NULL;
