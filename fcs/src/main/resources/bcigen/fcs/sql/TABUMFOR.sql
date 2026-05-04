CREATE TABLE IF NOT EXISTS TABUMFOR
(
    tenant varchar(10),
    bstme varchar(3),
    datab date,
    lifnr varchar(10),
    matnr varchar(18),
    PRIMARY KEY (tenant,bstme,datab,lifnr,matnr)
);
ALTER TABLE TABUMFOR ADD COLUMN bstmexpallet int;
ALTER TABLE TABUMFOR ALTER COLUMN bstmexpallet TYPE int;
ALTER TABLE TABUMFOR ADD COLUMN meins varchar(3);
ALTER TABLE TABUMFOR ALTER COLUMN meins TYPE varchar(3);
ALTER TABLE TABUMFOR ADD COLUMN mengexbstme numeric(10,3);
ALTER TABLE TABUMFOR ALTER COLUMN mengexbstme TYPE numeric(10,3);
