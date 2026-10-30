# MBXA - DDT trasferimenti tra stabilimenti (mov. 301)

## Messa in funzione

1. **Database**: schema `ddt` già creato con `ddt_schema.sql` (database UTF8).
2. **Configurazione**: copiare `config/mbxa.properties.template` in
   `<tomcat>/conf/mbxa.properties` e compilare SAP, database e numerazione.
   In alternativa: `-Dmbxa.config=C:/percorso/mbxa.properties` o variabile `MBXA_CONFIG`.
   Il file viene riletto da solo quando cambia.
3. **Logo**: copiare `Logo_Lamplast.jpg` (quello del listino) come
   `src/main/resources/lamplast/mbxa/pdf/logo.jpg`, oppure indicare `pdf.logo` nel file di configurazione.
4. **SAP**: servono gli arrangement esistenti SAP_COM_0108 (documenti materiale)
   e SAP_COM_0009 (prodotto, ZFCS_SAP_COM_0009). Se i due utenti di comunicazione
   sono diversi, valorizzare anche `sap.product.user` / `sap.product.password`.
5. **Pagina iniziale**: `/mbxa/ddt_main.xml` (page bean `DdtMainUI`).

## Struttura

| Package | Contenuto |
|---|---|
| `logic.config` | `AppConfig`: lettura di `mbxa.properties` |
| `logic.sap` | client OData V2 (Basic Auth): documento materiale, prodotto |
| `logic.db` | JDBC su PostgreSQL (`DdtDao`, `AnagraficheDao`) |
| `logic.service` | casi d'uso (`DdtService`), regole su pesi, numerazione, fotografia dei dati stampati |
| `logic.pdf` | `DdtPdfBuilder` (OpenPDF), layout ricalcato sulla MBXA di R/3 |
| `view.managedbeans` | `DdtMainUI`, `DdtDettaglioUI` (popup), `AnagraficheUI` (popup) |

## Regole principali

- Un DDT per documento materiale. Si stampano solo le righe di uscita del 301
  (la contropartita creata automaticamente da SAP è esclusa).
- **Numerazione MANUALE**: l'operatore inserisce il numero bolla letto in MIGO
  (GR/GI slip number, non esposto dalle API di S/4HANA Cloud PE).
  Controlli: solo cifre, unico per anno, sequenza secondo `ddt.numerazione.controllo.sequenza`.
- **Numerazione INTERNA**: progressivo per anno assegnato all'emissione da `ddt_numeratore`
  (nessun buco, sicuro anche con emissioni contemporanee).
- La prima stampa emette il DDT. Dopo l'emissione si modificano solo i dati accessori:
  la stampa successiva crea una nuova versione se i dati sono cambiati, altrimenti
  restituisce lo stesso PDF, byte per byte. Nessuna dicitura "copia" o "ristampa".
- Le versioni stampate e i DDT emessi non si cancellano (trigger del database).
  Un DDT si annulla, e i suoi PDF restano consultabili e ristampabili identici.
- Un solo DDT non annullato per documento materiale. Dopo un annullamento per errore
  (documento non stornato in SAP) il 301 torna tra quelli da acquisire: il nuovo DDT
  è collegato a quello annullato. Numerazione MANUALE: il numero del DDT annullato torna
  libero. Numerazione INTERNA: il progressivo non si riusa mai.
- Se il documento materiale risulta stornato in SAP, il dettaglio lo segnala e propone
  l'annullamento, che resta comunque un'azione dell'operatore. Un 301 stornato non torna
  tra quelli da acquisire.
- La stampa apre il PDF in una scheda del browser (sempre la stessa): si stampa da lì.

## Aggiornamento di un database esistente

`ddt_migrazione_01_annullamenti.sql`: da eseguire una volta sui database creati con la
prima versione di `ddt_schema.sql` (i database nuovi non ne hanno bisogno).

## Backup

`pg_dump -Fc` notturno del database `ddt_trasf`, copiato fuori dal server:
contiene anche i PDF emessi (tabella `ddt_versione`).
