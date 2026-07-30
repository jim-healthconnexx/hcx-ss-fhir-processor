CREATE TABLE healthdata.panel (
                                  panel_id int4 GENERATED ALWAYS AS IDENTITY( INCREMENT BY 1 MINVALUE 1 MAXVALUE 2147483647 START 1 CACHE 1 NO CYCLE) NOT NULL,
                                  customer_id int4 NOT NULL,
                                  reference_number varchar(255) NULL,
                                  status varchar(255) NULL,
                                  created_on timestamptz NULL,
                                  completed_on timestamptz NULL,
                                  lookback int4 NULL,
                                  data_source varchar(255) NULL,
                                  start_date date NULL,
                                  end_date date NULL,
                                  product_id int4 NULL,
                                  sent_request_filename varchar(255) NULL,
                                  last_updated timestamptz NULL,
                                  CONSTRAINT xpk_panel PRIMARY KEY (panel_id)
);