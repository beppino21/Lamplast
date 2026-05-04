CREATE TABLE IF NOT EXISTS TABFCST001
(
    tenant varchar(10),
    mtart varchar(4),
    werks varchar(4),
    PRIMARY KEY (tenant,mtart,werks)
);
ALTER TABLE TABFCST001 ADD COLUMN exp2fcs boolean;
ALTER TABLE TABFCST001 ALTER COLUMN exp2fcs TYPE boolean;
