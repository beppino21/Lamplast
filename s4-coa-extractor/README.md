# S/4HANA Chart of Accounts Extractor

Estrae le anagrafiche del piano dei conti da **S/4HANA Cloud Public Edition** via OData API
e produce un file CSV con codice conto, descrizione breve e descrizione lunga.

## Requisiti

| Strumento | Versione minima |
|-----------|----------------|
| Java JDK  | 21              |
| Maven     | 3.8+            |

## Build

```bash
mvn clean package -q
```

Produce `target/s4-coa-extractor-1.0.0.jar` (fat JAR con tutte le dipendenze).

---

## Configurazione

Copiare e adattare `config.properties`:

```properties
# Connessione
s4.base.url=https://myXXXXXX.s4hana.ondemand.com
s4.username=IL_TUO_UTENTE
s4.password=LA_TUA_PASSWORD

# Estrazione
s4.chart.of.accounts=INT      # es: INT, KONS, CA10
s4.language=IT                 # IT, EN, DE, FR, ES...

# Output
output.directory=/var/data/coa
output.filename=coa_export_yyyyMMdd.csv
output.separator=;
```

### Protezione del file di configurazione (Linux)

La password è in chiaro nel file. Limitare i permessi:

```bash
chmod 600 config.properties
chown app_user:app_group config.properties
```

---

## Esecuzione

```bash
# Passando il percorso della configurazione come argomento
java -jar target/s4-coa-extractor-1.0.0.jar /etc/coa/config.properties

# Oppure con variabile d'ambiente
export COA_CONFIG_FILE=/etc/coa/config.properties
java -jar target/s4-coa-extractor-1.0.0.jar

# Con directory di log personalizzata
java -Dlog.dir=/var/log/coa-extractor -jar target/s4-coa-extractor-1.0.0.jar /etc/coa/config.properties
```

### Exit code

| Codice | Significato                    |
|--------|-------------------------------|
| 0      | Successo                      |
| 1      | Errore di configurazione      |
| 2      | Errore comunicazione S/4HANA  |
| 3      | Errore scrittura file CSV     |
| 99     | Errore generico               |

---

## Schedulazione

### Linux / macOS (cron)

```bash
crontab -e
```

Aggiungere (esempio: ogni giorno alle 06:00):

```cron
0 6 * * * /usr/bin/java -Dlog.dir=/var/log/coa-extractor \
    -jar /opt/coa-extractor/s4-coa-extractor-1.0.0.jar \
    /etc/coa/config.properties \
    >> /var/log/coa-extractor/cron.log 2>&1
```

> **Nota:** In cron usare sempre percorsi assoluti per JAR, config e output directory.

### Windows (Task Scheduler)

Creare un file `run-extractor.bat`:

```bat
@echo off
"C:\Program Files\Java\jdk-21\bin\java.exe" ^
    -Dlog.dir=C:\Logs\coa-extractor ^
    -jar C:\Apps\coa-extractor\s4-coa-extractor-1.0.0.jar ^
    C:\Config\coa\config.properties
exit /b %ERRORLEVEL%
```

Poi in Task Scheduler:
- **Program/script:** `C:\Apps\coa-extractor\run-extractor.bat`
- **Start in:** `C:\Apps\coa-extractor`
- Spuntare *"Run whether user is logged on or not"*
- Spuntare *"Do not store password"* se il servizio non accede a risorse di rete

---

## Formato CSV prodotto

```
CodiceConto;DescrizioneBreve;DescrizioneLunga
0001000;Cassa;Cassa e valori in cassa
0001010;Banca;Disponibilità su c/c bancari
...
```

- Encoding: **UTF-8 con BOM** (per compatibilità Excel su Windows)
- Separatore: configurabile (default `;`)
- Celle con separatore o doppi apici: quotate automaticamente (RFC 4180)
- Nome file: supporta pattern di data (es. `coa_yyyyMMdd.csv`)

---

## Struttura progetto

```
s4-coa-extractor/
├── pom.xml
├── config.properties               ← template configurazione
└── src/main/java/com/example/coaextractor/
    ├── Main.java                   ← entry point
    ├── config/
    │   ├── AppConfig.java          ← lettura/validazione configurazione
    │   └── ConfigException.java
    ├── client/
    │   ├── S4HanaClient.java       ← chiamate OData con paging e retry
    │   └── S4HanaClientException.java
    ├── model/
    │   └── GlAccount.java          ← record dati
    └── writer/
        └── CsvWriter.java          ← scrittura CSV
```

---

## Note tecniche

### API OData utilizzata

`API_GLACCOUNTS_SRV` → entity set `GLAccountInChartOfAccounts` con `$expand=to_Text`

Il campo **`GLAccountName`** corrisponde alla *descrizione breve* (20 car.),
**`GLAccountLongName`** alla *descrizione lunga* (50 car.).

### Paging

Il client usa `$top` / `$skip` per paginare i risultati (dimensione pagina configurabile,
default 500). Il paging termina quando il server restituisce meno record del `$top` richiesto.

### Lingua

Il filtro per lingua avviene **lato client** dopo l'expand, per massima compatibilità
con gateway SAP che non supportano filtri su navigation property nel `$expand`.
L'header `sap-language` viene comunque inviato ad ogni richiesta.

### Dipendenze (licenze libere)

| Libreria       | Versione | Licenza       |
|----------------|----------|---------------|
| Jackson Databind | 2.17.1  | Apache 2.0    |
| Logback Classic  | 1.5.6   | EPL 1.0 / LGPL 2.1 |
