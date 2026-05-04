CREATE TABLE IF NOT EXISTS TABUMCLI
(
    tenant varchar(10),
    bstme varchar(3),
    datab date,
    kunnr varchar(10),
    matnr varchar(18),
    PRIMARY KEY (tenant,bstme,datab,kunnr,matnr)
);
ALTER TABLE TABUMCLI ADD COLUMN bstmexpallet int;
ALTER TABLE TABUMCLI ALTER COLUMN bstmexpallet TYPE int;
ALTER TABLE TABUMCLI ADD COLUMN meins varchar(3);
ALTER TABLE TABUMCLI ALTER COLUMN meins TYPE varchar(3);
ALTER TABLE TABUMCLI ADD COLUMN mengexbstme numeric(10,3);
ALTER TABLE TABUMCLI ALTER COLUMN mengexbstme TYPE numeric(10,3);
