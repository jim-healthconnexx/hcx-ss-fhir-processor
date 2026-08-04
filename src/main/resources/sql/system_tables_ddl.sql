--
-- DDL statements to create custom tables for intake processor
--

-- HDC-138: ALTER TABLE for existing environments (run once on QA/prod before deploy):
-- ALTER TABLE healthdata.customer ADD COLUMN s3_bucket varchar(255) NULL;

CREATE TABLE healthdata.customer (
    customer_id int GENERATED ALWAYS AS IDENTITY,
    uuid uuid,
    name varchar(255) NULL,
    -- HDC-138: actual S3 bucket name (e.g. hcx-intake-qa); must be populated before use
    s3_bucket varchar(255) NULL,
    -- HDC-15: renamed from incoming_bucket to bucket; used as the per-customer key prefix in S3
    bucket varchar(255) NULL,
    incoming_request_location varchar(255) NULL,
    -- HDC-25: renamed from processed_location
    request_processed_location varchar(255) NULL,
    outgoing_request_location varchar(255) NULL,
    -- HDC-25: location where the sent request is stored (used by external application)
    request_sent_location varchar(255) NULL,
    error_location varchar(255) NULL,
    active boolean NULL,
    CONSTRAINT xpk_customer PRIMARY KEY (customer_id));

CREATE TABLE healthdata.file (
    file_id int GENERATED ALWAYS AS IDENTITY,
    customer_id int,
    name varchar(255) NULL,
    source varchar(255) NULL,
    source_uuid uuid NULL,
    reference_number varchar(255) NULL,
    status varchar(255) NULL,
    created_on timestamptz NULL,
    CONSTRAINT xpk_file PRIMARY KEY (file_id));


CREATE TABLE healthdata.panel (
    panel_id int GENERATED ALWAYS AS IDENTITY,
    customer_id int not null,
    reference_number varchar(255) NULL,
    status varchar(255) NULL,
    created_on timestamptz NULL,
    completed_on timestamptz NULL,
    lookback int,
    data_source varchar(255),
    start_date date,
    end_date date,
    product_id int,
    -- HDC-25: filename of the sent request file generated for this panel
    sent_request_filename varchar(255) NULL,
    last_updated timestamptz NULL,
    CONSTRAINT xpk_panel PRIMARY KEY (panel_id));

CREATE TABLE healthdata.patient (
	patient_id int GENERATED ALWAYS AS IDENTITY,
	panel_id int NOT NULL,
	sequence_number int NOT NULL,
	mrn varchar(255) NOT NULL,
	last_name varchar(255) NOT NULL,
	first_name varchar(255) NOT NULL,
	middle_name varchar(255) NULL,
	prefix varchar(255) NULL,
	suffix varchar(255) NULL,
	address_line_1 varchar(255) NULL,
	address_line_2 varchar(255) NULL,
	city varchar(255) NULL,
	state varchar(255) NULL,
	postal_code varchar(255) NOT NULL,
	home_phone varchar(255) NULL,
	alt_phone varchar(255) NULL,
	date_of_birth date NOT NULL,
	gender varchar(255) NOT NULL,
	physician_npi varchar(255) NOT NULL,
    physician_name varchar(255) NOT NULL,
	consent varchar(255) NOT NULL,
	CONSTRAINT PK_patient PRIMARY KEY (patient_id));

CREATE TABLE healthdata.product (
    product_id int GENERATED ALWAYS AS IDENTITY,
    code varchar(255) NULL,
    name varchar(255) NULL,
    description varchar(255) NULL,
    effective_date date NULL,
    inactive_date date NULL,
    file_config json NULL,
    CONSTRAINT xpk_product PRIMARY KEY (product_id));

CREATE TABLE healthdata.ss_patient_response (
    ss_patient_response_id int GENERATED ALWAYS AS IDENTITY,
    file_name varchar(255) NOT NULL,
    processed_on timestamptz NOT NULL,
    shd_version varchar(255) NOT NULL,
    shd_receiver_id varchar(255) NOT NULL,
    shd_sender_id varchar(255) NOT NULL,
    shd_transaction_control_number varchar(255) NOT NULL,
    shd_transaction_date varchar(255) NOT NULL,
    shd_transaction_time varchar(255) NOT NULL,
    shd_transaction_file_type varchar(255) NULL,
    shd_transmission_control_number varchar(255) NOT NULL,
    shd_transmission_date varchar(255) NOT NULL,
    shd_transmission_time varchar(255) NOT NULL,
    shd_file_type varchar(255) NOT NULL,
    shd_load_status varchar(255) NOT NULL,
    shd_load_status_description varchar(255) NOT NULL,
    str_processed_record_count int NOT NULL,
    str_error_record_count int NOT NULL,
    str_loaded_record_count int NOT NULL,
    str_total_error_count int NOT NULL,
    CONSTRAINT PK_ss_patient_response PRIMARY KEY (ss_patient_response_id)
    );


CREATE TABLE healthdata.ss_patient_response_detail (
    ss_patient_response_detail_id int GENERATED ALWAYS AS IDENTITY,
    ss_patient_response_id int NOT NULL,
    record_sequence_number int NULL,
    source_record_sequence_number int NULL,
    assigning_authority  varchar(255)  NULL,
    patient_id  varchar(255)  NULL,
    error_type varchar(255)  NULL,
    error_code varchar(255)  NULL,
    error_description  varchar(255)  NULL,
    CONSTRAINT PK_ss_patient_response_detail PRIMARY KEY (ss_patient_response_detail_id));

CREATE TABLE healthdata.ss_rx_history_response (
    ss_rx_history_response_id int GENERATED ALWAYS AS IDENTITY,
    patient_file_id varchar(250) NOT NULL,
    transmission_control_number varchar(10) NOT NULL,
    file_name varchar(250) NOT NULL,
    sent_time timestamptz NOT NULL,
    total_count int NOT NULL,
    patient_count int NOT NULL,
    ok_count int NOT NULL,
    multiple_response_count int NOT NULL,
    empty_count int NOT NULL,
    not_found_count int NOT NULL,
    incomplete_count int NOT NULL,
    error_count int NOT NULL,
    unknown_count int NOT NULL,
    inserted_on timestamptz NOT NULL,
    updated_on timestamptz NULL,
    CONSTRAINT PK_ss_rx_history_response PRIMARY KEY (ss_rx_history_response_id)
    );

CREATE TABLE healthdata.ss_rx_history_response_detail (
    ss_rx_history_response_detail_id int GENERATED ALWAYS AS IDENTITY,
    ss_rx_history_response_id int NOT NULL,
    fhir json NOT NULL,
    inserted_on timestamptz NOT NULL,
    CONSTRAINT PK_ss_rx_history_response_detail PRIMARY KEY (ss_rx_history_response_detail_id));