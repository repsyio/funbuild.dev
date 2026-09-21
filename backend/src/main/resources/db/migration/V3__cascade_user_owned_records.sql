ALTER TABLE assignments DROP CONSTRAINT assignments_created_by_fkey;
ALTER TABLE assignments
    ADD CONSTRAINT assignments_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE tech_labels DROP CONSTRAINT tech_labels_created_by_fkey;
ALTER TABLE tech_labels
    ADD CONSTRAINT tech_labels_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE CASCADE;
