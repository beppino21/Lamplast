package lamplast.utility.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lamplast.utility.config.SapConfiguration;
import lamplast.utility.model.ScheduleLineData;
import lamplast.utility.service.SapAuthenticationService.SapAuthToken;

/**
 * Gestisce le operazioni CRUD sulle schedulazioni SAP.
 * Include il metodo dryRun() per la verifica preventiva senza modifiche.
 *
 * NOVITA' - supporto multi-variante API:
 * Gli ordini SAP possono appartenere a categorie documento (VBTYP) diverse.
 * API_SALES_ORDER_SRV copre SOLO la categoria 'C' (ordini standard); gli
 * ordini "senza addebito" (categoria 'I', es. tipo CBFD) richiedono il
 * servizio separato API_SALES_ORDER_WITHOUT_CHARGE_SRV (vedi KBA SAP
 * 3621002 e 2752419). Poiche' il file Excel di input non indica il tipo
 * ordine, il chiamante deve invocare resolveOrderVariants() una volta,
 * subito dopo il caricamento del file (o comunque prima del dry-run),
 * passando l'elenco degli ordini distinti presenti: la categoria di
 * ciascun ordine viene risolta con una query $filter sulla collezione
 * (non una GET su chiave), che restituisce HTTP 200 con array vuoto per
 * un ordine filtrato fuori dalla vista — a differenza della GET su
 * chiave, che risponde 404 sia per "ordine non di questa categoria" sia
 * per "ordine inesistente", rendendo i due casi indistinguibili. Il
 * risultato viene memorizzato in una cache per ordine, usata poi in modo
 * deterministico da dryRun()/updateScheduleLine() per l'intera sessione
 * di elaborazione del file.
 */
public class SapScheduleLineService {

    private final SapConfiguration        config;
    private final SapAuthenticationService authService;
    private final HttpClient               httpClient;

    /**
     * Cache "ordine SAP normalizzato" -> variante API risolta.
     * Vive per tutta la vita di questa istanza di servizio (dry-run +
     * aggiornamento reale condividono la stessa istanza nel managed bean),
     * quindi la risoluzione avviene una sola volta per ordine per l'intera
     * sessione di elaborazione di un file Excel.
     */
    private final Map<String, SalesOrderApiVariant> variantCache = new ConcurrentHashMap<>();

    public SapScheduleLineService(SapConfiguration config) {
        this.config      = config;
        this.authService = new SapAuthenticationService(config);
        this.httpClient  = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();
    }

    // -------------------------------------------------------
    // RISOLUZIONE IN BLOCCO DELLA CATEGORIA DOCUMENTO (al caricamento file)
    // -------------------------------------------------------

    /**
     * Da chiamare una volta, subito dopo aver caricato il file Excel
     * (prima del dry-run), passando l'elenco degli ordini presenti
     * (anche con duplicati: vengono deduplicati qui). Per ogni ordine
     * non ancora in cache, determina con certezza se è di categoria
     * standard (C) o senza addebito (I) tramite una query $filter — a
     * differenza di una GET su chiave, un ordine filtrato fuori dalla
     * vista risponde 200 con array vuoto, non 404, quindi non c'è
     * ambiguità con "l'ordine non esiste".
     * <p>
     * Se un ordine non viene trovato in nessuna delle due varianti (numero
     * errato, ordine non ancora creato, ecc.), non viene messo in cache:
     * il resto del flusso lo tratterà come "non trovato" tramite la
     * normale GET su chiave, con lo stesso comportamento di sempre.
     *
     * @param orderNumbers   numeri ordine così come appaiono nell'Excel
     *                       (anche non normalizzati/duplicati)
     * @param progressMessage callback opzionale invocata con una riga di
     *                       log leggibile per ogni ordine risolto (per
     *                       mostrare avanzamento in UI); può essere null
     */
    public void resolveOrderVariants(java.util.Collection<String> orderNumbers,
                                     java.util.function.Consumer<String> progressMessage) throws Exception {

        java.util.LinkedHashSet<String> distinctOrders = new java.util.LinkedHashSet<>();
        for (String raw : orderNumbers) {
            if (raw != null && !raw.isBlank()) {
                distinctOrders.add(normalizeOrderNumber(raw));
            }
        }

        int i = 0;
        for (String order : distinctOrders) {
            i++;
            if (variantCache.containsKey(order)) continue; // già risolto (es. run precedente)

            String esito;
            if (existsInVariant(order, SalesOrderApiVariant.STANDARD)) {
                variantCache.put(order, SalesOrderApiVariant.STANDARD);
                esito = "ordine di vendita standard";
            } else if (existsInVariant(order, SalesOrderApiVariant.WITHOUT_CHARGE)) {
                variantCache.put(order, SalesOrderApiVariant.WITHOUT_CHARGE);
                esito = "ordine senza addebito (gratuito)";
            } else {
                esito = "non trovato su nessuna delle due API — verrà segnalato come mancante";
            }

            if (progressMessage != null) {
                progressMessage.accept("Ordine " + order + " (" + i + "/" + distinctOrders.size()
                    + ") — " + esito);
            }
        }
    }

    /**
     * Verifica se l'ordine esiste per la variante indicata, tramite una
     * query $filter sulla collezione delle posizioni (top 1, nessuna
     * espansione). Restituisce true solo se la chiamata va a buon fine
     * (HTTP 200) e produce almeno un risultato.
     */
    private boolean existsInVariant(String order, SalesOrderApiVariant variant) throws Exception {
        String filterClause = variant.orderKeyField + "%20eq%20'" + order + "'";
        String url = variant.baseUrl(config)
            + variant.itemEntitySet
            + "?$filter=" + filterClause
            + "&$top=1&$select=" + variant.orderKeyField
            + "&sap-client=" + config.getClient();

        HttpResponse<String> resp = doGet(url);
        if (resp.statusCode() != 200) return false;
        return hasResults(resp.body());
    }

    @SuppressWarnings("unchecked")
    private boolean hasResults(String body) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> json = mapper.readValue(body, java.util.Map.class);
            java.util.Map<String, Object> d    =
                (java.util.Map<String, Object>) json.get("d");
            if (d == null) return false;
            Object results = d.get("results");
            return results instanceof java.util.List && !((java.util.List<?>) results).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------
    // DRY RUN - verifica preventiva senza modifiche
    // -------------------------------------------------------

    /**
     * Verifica preventiva: interroga SAP in sola lettura e restituisce
     * una descrizione di cosa accadra' (o perche' fallira').
     * Non esegue nessuna modifica su SAP.
     */
    public SapDryRunResult dryRun(ScheduleLineData data) throws Exception {

        String validationError = data.validate();
        if (validationError != null) {
            return SapDryRunResult.error("Dati non validi: " + validationError);
        }

        String paddedItem  = String.format("%06d", data.getItemNumber());
        String paddedSched = String.format("%04d", Math.abs(data.getScheduleLine()));

        if (data.isInsert()) {
            return checkPositionExists(data, paddedItem);
        } else {
            return checkScheduleLineExists(normalizeOrderNumber(data.getOrderNumber()), paddedItem, paddedSched, data);
        }
    }

    /**
     * Verifica esistenza della posizione ordine (per inserimento).
     * Controlla anche che il materiale nel file coincida con quello SAP.
     * GET A_SalesOrderItem(SalesOrder='X', SalesOrderItem='000010')
     * (o l'equivalente sulla variante "senza addebito", se applicabile)
     */
    private SapDryRunResult checkPositionExists(ScheduleLineData data,
                                                String paddedItem) throws Exception {

        String order = normalizeOrderNumber(data.getOrderNumber());

        VariantProbe probe = resolveForRead(order, v -> doGet(itemUrl(v, order, paddedItem)));
        HttpResponse<String> resp = probe.response;

        if (resp.statusCode() == 200) {
            // Controllo materiale - tollerante agli zeri iniziali
            String materialMismatch = checkMaterialMatch(resp.body(), data.getMaterial());
            if (materialMismatch != null) {
                return SapDryRunResult.error(
                    "Posizione " + paddedItem + " - " + materialMismatch);
            }
            if (probe.variant == SalesOrderApiVariant.WITHOUT_CHARGE) {
                // A_SlsOrdWthoutChrgSchedLine e' sola lettura su SAP
                // (creatable="false" nel $metadata): l'inserimento di una
                // nuova schedulazione via questa API non e' possibile.
                return SapDryRunResult.warning(
                    "NON_GESTIBILE:ordine senza addebito — inserimento schedulazione non supportato dall'API SAP, gestire manualmente in VA02");
            }
            return SapDryRunResult.ok(
                "Posizione " + paddedItem + " esistente - inserimento possibile"
                + varianteLabel(probe.variant));
        } else if (resp.statusCode() == 404) {
            return SapDryRunResult.error(
                "Posizione " + paddedItem + " NON trovata su SAP - inserimento impossibile");
        } else {
            return SapDryRunResult.warning(
                "Posizione " + paddedItem + " - risposta SAP inattesa: HTTP " + resp.statusCode());
        }
    }

    /**
     * Confronta il materiale del file con quello presente sulla posizione SAP.
     * Il confronto e' numerico/tollerante: "100012" == "000100012".
     *
     * @return null se i materiali coincidono, stringa di errore altrimenti.
     */
    @SuppressWarnings("unchecked")
    private String checkMaterialMatch(String body, String materialFromFile) {
        if (materialFromFile == null || materialFromFile.isBlank()) {
            // Materiale non comunicato nel file: controllo non applicabile
            return null;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> json = mapper.readValue(body, java.util.Map.class);
            java.util.Map<String, Object> d    =
                (java.util.Map<String, Object>) json.get("d");
            if (d == null) return null; // non leggibile, non blocchiamo

            String sapMaterial  = String.valueOf(d.getOrDefault("Material", "")).trim();
            String fileMaterial = materialFromFile.trim();

            // Normalizzazione: rimuoviamo leading zeros e confrontiamo
            String sapNorm  = sapMaterial.replaceFirst("^0+(?!$)", "");
            String fileNorm = fileMaterial.replaceFirst("^0+(?!$)", "");

            if (sapNorm.equalsIgnoreCase(fileNorm)) return null; // OK

            return "Materiale non corrispondente: file='" + fileMaterial
                 + "' SAP='" + sapMaterial + "'";
        } catch (Exception e) {
            return null; // parsing fallito: non blocchiamo
        }
    }

    /**
     * Verifica esistenza della schedulazione (per modifica/eliminazione).
     * Prima controlla la coerenza del materiale sulla posizione (bloccante),
     * poi verifica schedulazione, quantita' e data.
     */
    private SapDryRunResult checkScheduleLineExists(String order,
                                                    String paddedItem,
                                                    String paddedSched,
                                                    ScheduleLineData data) throws Exception {

        // --- Controllo materiale sulla posizione (GET A_SalesOrderItem) ---
        // Bloccante: se il materiale nel file non corrisponde a quello SAP,
        // i due sistemi sono disallineati e non possiamo procedere.
        if (data.getMaterial() != null && !data.getMaterial().isBlank()) {
            VariantProbe materialProbe = resolveForRead(order, v -> doGet(itemUrl(v, order, paddedItem)));
            HttpResponse<String> itemResp = materialProbe.response;

            if (itemResp.statusCode() == 200) {
                String materialMismatch = checkMaterialMatch(itemResp.body(), data.getMaterial());
                if (materialMismatch != null) {
                    return SapDryRunResult.error(
                        "Posizione " + paddedItem + " - " + materialMismatch
                        + " - operazione bloccata per disallineamento dati");
                }
            } else if (itemResp.statusCode() == 404) {
                return SapDryRunResult.error(
                    "Posizione " + paddedItem + " NON trovata su SAP - operazione impossibile");
            }
            // Altri status HTTP: non blocchiamo sul materiale, proseguiamo
        }

        // --- Verifica schedulazione ---
        // Riusa la variante gia' risolta per questo ordine (dal controllo
        // materiale sopra, o da una riga precedente dello stesso ordine);
        // se non ancora nota, la risolve qui con lo stesso meccanismo.
        VariantProbe probe = resolveForRead(order,
            v -> doGet(scheduleLineReadUrl(v, order, paddedItem, paddedSched)));
        HttpResponse<String> resp = probe.response;
        String varLabel = varianteLabel(probe.variant);

        boolean isElimination = isQuantityZero(data.getQuantity());

        if (resp.statusCode() == 200) {
            if (probe.variant == SalesOrderApiVariant.WITHOUT_CHARGE) {
                // A_SlsOrdWthoutChrgSchedLine e' sola lettura e non espone
                // RequestedDeliveryDate/ScheduleLineOrderQuantity: non e'
                // possibile confrontare né applicare modifiche a questo
                // livello per gli ordini senza addebito.
                return SapDryRunResult.warning(
                    "NON_GESTIBILE:ordine senza addebito — modifica/eliminazione schedulazione non supportata dall'API SAP, gestire manualmente in VA02");
            }
            if (isElimination) {
                return SapDryRunResult.ok(
                    "Schedulazione " + paddedSched + " trovata - sara' eliminata (qty=0)" + varLabel);
            }
            String detail = extractCurrentValues(resp.body(), data);
            if ("NESSUNA_MODIFICA".equals(detail)) {
                return SapDryRunResult.warning("NESSUNA_MODIFICA");
            }
            if ("EVASA".equals(detail)) {
                return SapDryRunResult.warning("EVASA");
            }
            if (detail != null && detail.startsWith("BLOCCATA:")) {
                String cat = detail.substring("BLOCCATA:".length());
                return SapDryRunResult.warning("BLOCCATA:" + cat);
            }
            return SapDryRunResult.ok("Schedulazione " + paddedSched + " trovata - " + detail + varLabel);

        } else if (resp.statusCode() == 404) {
            return SapDryRunResult.error(
                "Schedulazione " + paddedSched + " NON trovata su SAP"
                + (isElimination ? " - nulla da eliminare" : " - modifica impossibile"));
        } else {
            SapErrorInfo err = extractSapError(resp.body());

            if (isCategoryMismatch(err)) {
                // A questo punto resolveForRead ha gia' tentato entrambe le
                // varianti: se siamo comunque qui, nessuna delle due ha
                // riconosciuto l'ordine come categoria supportata.
                return SapDryRunResult.error(
                    "Schedulazione " + paddedSched
                    + " - tipo documento non gestito da nessuna delle API configurate (codice: " + err.code + ")");
            }
            return SapDryRunResult.warning(
                "Schedulazione " + paddedSched
                + " - risposta SAP inattesa: HTTP " + resp.statusCode()
                + (err.message.isEmpty() ? "" : " - " + err.message));
        }
    }

    /**
     * Estrae quantita' e data attuali dalla risposta SAP e le confronta
     * con i valori nuovi, producendo un testo descrittivo per il dry-run.
     */
    @SuppressWarnings("unchecked")
    private String extractCurrentValues(String body, ScheduleLineData data) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> json = mapper.readValue(body, java.util.Map.class);
            java.util.Map<String, Object> d    =
                (java.util.Map<String, Object>) json.get("d");
            if (d == null) return "dati attuali non leggibili";

            // --- Controlla stato consegna ---
            Object deliveredQtyObj = d.get("DeliveredQtyInOrderQtyUnit");
            Object openQtyObj      = d.get("OpenConfdDelivQtyInOrdQtyUnit");

            if (deliveredQtyObj != null) {
                double deliveredQty = toDouble(deliveredQtyObj.toString());
                if (!Double.isNaN(deliveredQty) && deliveredQty > 0.0) {
                    double openQty = openQtyObj != null
                        ? toDouble(openQtyObj.toString()) : Double.NaN;
                    boolean fullyDelivered = !Double.isNaN(openQty) && openQty <= 0.0;
                    if (fullyDelivered) return "BLOCCATA:EVASA";

                    double newQty = toDouble(data.getQuantity() != null
                        ? data.getQuantity().replace(",", ".") : "0");
                    if (!Double.isNaN(newQty) && newQty < deliveredQty) {
                        return "BLOCCATA:QTA_SOTTO_CONSEGNATO:" + deliveredQty;
                    }
                }
            }

            // --- Confronto quantita' ---
            String currentQtyStr = String.valueOf(d.getOrDefault("ScheduleLineOrderQuantity", "?"));
            String newQtyStr     = data.getQuantity() != null ? data.getQuantity() : "?";
            boolean qtyChanged   = !toDouble(currentQtyStr).equals(toDouble(newQtyStr));

            // --- Confronto data ---
            String currentDate = parseSapDate(
                    String.valueOf(d.getOrDefault("RequestedDeliveryDate", "?")));
            String newDate = data.getProductionDate() != null
                ? data.getProductionDate().format(
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "?";
            boolean dateChanged = !currentDate.equals(newDate);

            if (!qtyChanged && !dateChanged) return "NESSUNA_MODIFICA";

            StringBuilder sb = new StringBuilder("modifica prevista:");
            if (qtyChanged) sb.append(" Qta ").append(fmt(currentQtyStr)).append("->").append(fmt(newQtyStr));
            if (dateChanged) sb.append(" Data ").append(currentDate).append("->").append(newDate);
            return sb.toString();

        } catch (Exception e) {
            return "dati attuali non leggibili";
        }
    }

    // -------------------------------------------------------
    // ENTRY POINT PUBBLICO - aggiornamento reale
    // -------------------------------------------------------

    public SapResponse updateScheduleLine(ScheduleLineData data) throws Exception {

        String validationError = data.validate();
        if (validationError != null) {
            SapResponse r = new SapResponse(400);
            r.setSapMessage("Validazione: " + validationError);
            return r;
        }

        String order = normalizeOrderNumber(data.getOrderNumber());
        SalesOrderApiVariant cached  = variantCache.get(order);
        SalesOrderApiVariant variant = cached != null ? cached : SalesOrderApiVariant.STANDARD;

        // Se l'ordine e' gia' noto come "senza addebito", non tentiamo
        // nemmeno la scrittura: A_SlsOrdWthoutChrgSchedLine e' sola lettura
        // su SAP (creatable="false"/"updatable="false" nel $metadata) — la
        // richiesta sarebbe comunque respinta. Evitiamo la chiamata a vuoto
        // e diamo subito un messaggio chiaro e azionabile.
        if (variant == SalesOrderApiVariant.WITHOUT_CHARGE) {
            SapResponse r = new SapResponse(400);
            r.setSapMessage("Ordine senza addebito (categoria I) — "
                + "aggiornamento/inserimento schedulazione non supportato dall'API SAP "
                + "(A_SlsOrdWthoutChrgSchedLine è sola lettura). Gestire manualmente in VA02.");
            return r;
        }

        SapAuthToken auth = authService.fetchCsrfToken(variant.baseUrl(config), variant.itemEntitySet);

        SapResponse response = data.isInsert()
            ? insertScheduleLine(data, auth, variant)
            : patchScheduleLine(data, auth, variant);

        // Fallback automatico: solo se la variante non era gia' nota per
        // questo ordine (altrimenti un errore genuino verrebbe scambiato
        // per un problema di categoria documento a ogni riga).
        if (cached == null && isCategoryMismatch(response)) {
            SalesOrderApiVariant alt = variant.other();

            if (alt == SalesOrderApiVariant.WITHOUT_CHARGE) {
                // La variante alternativa e' "senza addebito": la scrittura
                // non e' comunque possibile via API (vedi sopra). Segnaliamo
                // la causa reale invece di un generico errore SAP, e
                // mettiamo comunque in cache la variante corretta per le
                // righe successive dello stesso ordine.
                variantCache.put(order, alt);
                SapResponse r = new SapResponse(400);
                r.setSapMessage("Ordine senza addebito (categoria I) — "
                    + "aggiornamento/inserimento schedulazione non supportato dall'API SAP "
                    + "(A_SlsOrdWthoutChrgSchedLine è sola lettura). Gestire manualmente in VA02.");
                return r;
            }

            SapAuthToken altAuth = authService.fetchCsrfToken(alt.baseUrl(config), alt.itemEntitySet);

            SapResponse altResponse = data.isInsert()
                ? insertScheduleLine(data, altAuth, alt)
                : patchScheduleLine(data, altAuth, alt);

            if (!isCategoryMismatch(altResponse)) {
                variant  = alt;
                response = altResponse;
            }
        }

        variantCache.put(order, variant);
        return response;
    }

    // -------------------------------------------------------
    // INSERT (POST)
    // -------------------------------------------------------

    private SapResponse insertScheduleLine(ScheduleLineData data,
                                           SapAuthToken auth,
                                           SalesOrderApiVariant variant) throws Exception {

        String sapDate    = convertToSapDate(data.getProductionDate());
        String cleanQty   = cleanQuantity(data.getQuantity());
        String paddedItem = String.format("%06d", data.getItemNumber());
        String order      = normalizeOrderNumber(data.getOrderNumber());

        String payload = "{"
            + "\"" + variant.orderKeyField + "\":\""    + order       + "\","
            + "\"" + variant.itemKeyField  + "\":\""    + paddedItem  + "\","
            + "\"RequestedDeliveryDate\":\""     + sapDate               + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQty              + "\""
            + "}";

        String url = variant.baseUrl(config)
            + variant.scheduleLineEntitySet + "?sap-client=" + config.getClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization",  config.getBasicAuthHeader())
            .header("x-csrf-token",   auth.getCsrfToken())
            .header("Cookie",         auth.getCookies())
            .header("Content-Type",   "application/json")
            .header("Accept",         "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        return parseSapResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    // -------------------------------------------------------
    // UPDATE (PATCH)
    // -------------------------------------------------------

    private SapResponse patchScheduleLine(ScheduleLineData data,
                                          SapAuthToken auth,
                                          SalesOrderApiVariant variant) throws Exception {

        String sapDate     = convertToSapDate(data.getProductionDate());
        String cleanQty    = cleanQuantity(data.getQuantity());
        String paddedItem  = String.format("%06d", data.getItemNumber());
        String paddedSched = String.format("%04d", data.getScheduleLine());
        String order       = normalizeOrderNumber(data.getOrderNumber());

        String payload = "{"
            + "\"RequestedDeliveryDate\":\""     + sapDate  + "\","
            + "\"ScheduleLineOrderQuantity\":\"" + cleanQty + "\""
            + "}";

        String url = variant.baseUrl(config)
            + variant.scheduleLineEntitySet + "("
            + variant.orderKeyField + "='" + order      + "',"
            + variant.itemKeyField  + "='" + paddedItem + "',"
            + "ScheduleLine='"      + paddedSched        + "'"
            + ")?sap-client="       + config.getClient();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .method("PATCH", HttpRequest.BodyPublishers.ofString(payload))
            .header("Authorization",  config.getBasicAuthHeader())
            .header("x-csrf-token",   auth.getCsrfToken())
            .header("Cookie",         auth.getCookies())
            .header("Content-Type",   "application/json")
            .header("Accept",         "application/json")
            .header("If-Match",       "*")
            .build();

        return parseSapResponse(httpClient.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    // -------------------------------------------------------
    // RISOLUZIONE VARIANTE API (con fallback e cache per ordine)
    // -------------------------------------------------------

    /** Chiamata HTTP di lettura parametrizzata per variante, usata da resolveForRead. */
    @FunctionalInterface
    private interface HttpCall {
        HttpResponse<String> call(SalesOrderApiVariant variant) throws Exception;
    }

    /** Esito di una lettura risolta: variante usata + risposta ottenuta. */
    private static class VariantProbe {
        final SalesOrderApiVariant  variant;
        final HttpResponse<String> response;
        VariantProbe(SalesOrderApiVariant variant, HttpResponse<String> response) {
            this.variant  = variant;
            this.response = response;
        }
    }

    /**
     * Esegue una GET risolvendo automaticamente la variante API corretta:
     * - se l'ordine e' gia' in cache, usa direttamente quella (nessun tentativo a vuoto);
     * - altrimenti tenta prima STANDARD; se la risposta indica una categoria
     *   documento non supportata, ritenta con la variante alternativa;
     * - qualunque sia l'esito, memorizza in cache la variante "vincente"
     *   (o quella tentata per prima, se nessuna delle due ha dato un
     *   risultato utile) cosi' le righe successive dello stesso ordine non
     *   ripetono il probe.
     */
    private VariantProbe resolveForRead(String order, HttpCall call) throws Exception {
        SalesOrderApiVariant cached  = variantCache.get(order);
        SalesOrderApiVariant variant = cached != null ? cached : SalesOrderApiVariant.STANDARD;

        HttpResponse<String> resp = call.call(variant);

        if (cached == null && isCategoryMismatch(resp)) {
            SalesOrderApiVariant alt = variant.other();
            HttpResponse<String> altResp = call.call(alt);
            if (!isCategoryMismatch(altResp)) {
                variant = alt;
                resp    = altResp;
            }
        }

        variantCache.put(order, variant);
        return new VariantProbe(variant, resp);
    }

    private String varianteLabel(SalesOrderApiVariant variant) {
        return variant == SalesOrderApiVariant.WITHOUT_CHARGE ? " [ordine senza addebito]" : "";
    }

    // -------------------------------------------------------
    // COSTRUZIONE URL PER VARIANTE
    // -------------------------------------------------------

    private String itemUrl(SalesOrderApiVariant variant, String order, String paddedItem) {
        return variant.baseUrl(config)
            + variant.itemEntitySet + "("
            + variant.orderKeyField + "='" + order      + "',"
            + variant.itemKeyField  + "='" + paddedItem + "')"
            + "?$select=" + variant.orderKeyField + "," + variant.itemKeyField + ",Material"
            + "&sap-client=" + config.getClient();
    }

    private String scheduleLineReadUrl(SalesOrderApiVariant variant, String order,
                                       String paddedItem, String paddedSched) {
        String selectFields = variant.orderKeyField + "," + variant.itemKeyField
            + ",ScheduleLine,DeliveredQtyInOrderQtyUnit,OpenConfdDelivQtyInOrdQtyUnit";
        // Solo la variante STANDARD (A_SalesOrderScheduleLine) espone questi due
        // campi a livello di singola schedulazione. Su A_SlsOrdWthoutChrgSchedLine
        // (ordini senza addebito) non esistono: richiederli darebbe HTTP 400.
        if (variant == SalesOrderApiVariant.STANDARD) {
            selectFields += ",RequestedDeliveryDate,ScheduleLineOrderQuantity";
        }
        return variant.baseUrl(config)
            + variant.scheduleLineEntitySet + "("
            + variant.orderKeyField + "='" + order       + "',"
            + variant.itemKeyField  + "='" + paddedItem  + "',"
            + "ScheduleLine='"      + paddedSched         + "')"
            + "?$select=" + selectFields
            + "&sap-client=" + config.getClient();
    }

    // -------------------------------------------------------
    // RILEVAZIONE "CATEGORIA DOCUMENTO NON SUPPORTATA"
    // -------------------------------------------------------

    /** Codice + messaggio d'errore SAP estratti dal corpo di una risposta OData. */
    private static class SapErrorInfo {
        final String code;
        final String message;
        SapErrorInfo(String code, String message) {
            this.code    = code    != null ? code    : "";
            this.message = message != null ? message : "";
        }
    }

    @SuppressWarnings("unchecked")
    private SapErrorInfo extractSapError(String body) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> errJson = mapper.readValue(body, java.util.Map.class);
            java.util.Map<String, Object> error   =
                (java.util.Map<String, Object>) errJson.get("error");
            if (error == null) return new SapErrorInfo("", "");

            String code = String.valueOf(error.getOrDefault("code", ""));
            java.util.Map<String, Object> msg =
                (java.util.Map<String, Object>) error.get("message");
            String message = msg != null ? String.valueOf(msg.getOrDefault("value", "")) : "";
            return new SapErrorInfo(code, message);
        } catch (Exception e) {
            return new SapErrorInfo("", "");
        }
    }

    /**
     * Riconosce l'errore SAP "categoria documento non supportata da
     * quest'API" - il segnale che indica che va tentata l'altra variante.
     * Il codice CM_MGW_RT/020 e' confermato per API_SALES_ORDER_SRV
     * contro ordini di categoria 'I'; il controllo testuale e' un margine
     * di sicurezza per il caso simmetrico (API_SALES_ORDER_WITHOUT_CHARGE_SRV
     * contro ordini di categoria 'C'), il cui codice esatto non e' stato
     * verificato in questa sessione.
     */
    private boolean isCategoryMismatch(SapErrorInfo err) {
        if (err.code.contains("CM_MGW_RT/020")) return true;
        String m = err.message.toLowerCase();
        return m.contains("document categ")
            || m.contains("categoria docum")
            || m.contains("not supported for this business object");
    }

    /** Per risposte GET (dry-run): un vero 404 NON e' un mismatch di categoria. */
    private boolean isCategoryMismatch(HttpResponse<String> resp) {
        int status = resp.statusCode();
        if (status == 200 || status == 404) return false;
        return isCategoryMismatch(extractSapError(resp.body()));
    }

    /** Per risposte di scrittura (insert/patch). */
    private boolean isCategoryMismatch(SapResponse resp) {
        if (resp.isSuccess()) return false;
        if (resp.getHttpStatus() == 404) return false;
        return isCategoryMismatch(new SapErrorInfo(resp.getSapCode(), resp.getSapMessage()));
    }

    // -------------------------------------------------------
    // HTTP GET helper (per dry-run, senza CSRF)
    // -------------------------------------------------------

    private String normalizeOrderNumber(String raw) {
        return config.normalizeOrderNumber(raw);
    }

    private HttpResponse<String> doGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", config.getBasicAuthHeader())
            .header("Accept",        "application/json")
            .GET()
            .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    // -------------------------------------------------------
    // UTILITY
    // -------------------------------------------------------

    private String convertToSapDate(java.time.LocalDate date) {
        long millis = date.atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli();
        return "/Date(" + millis + ")/";
    }

    private String cleanQuantity(String quantity) {
        if (quantity == null || quantity.isBlank()) return "0";
        return quantity.replace(",", ".");
    }

    private boolean isQuantityZero(String qty) {
        if (qty == null || qty.isBlank()) return true;
        try { return Double.parseDouble(qty.replace(",", ".")) == 0; }
        catch (Exception e) { return false; }
    }

    private Double toDouble(String s) {
        if (s == null || s.isBlank() || s.equals("?")) return Double.NaN;
        try { return Double.parseDouble(s.replace(",", ".")); }
        catch (Exception e) { return Double.NaN; }
    }

    private String fmt(String s) {
        try {
            double v = Double.parseDouble(s.replace(",", "."));
            return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
        } catch (Exception e) { return s; }
    }

    private String parseSapDate(String sapDate) {
        try {
            if (sapDate == null || !sapDate.startsWith("/Date(")) return sapDate;
            long millis = Long.parseLong(sapDate.replaceAll("[^0-9]", ""));
            return java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) { return sapDate; }
    }

    private SapResponse parseSapResponse(HttpResponse<String> httpResponse) {
        SapResponse sapResponse = new SapResponse(httpResponse.statusCode());
        sapResponse.parseHeaders(httpResponse.headers().map());
        sapResponse.parseBody(httpResponse.body());
        return sapResponse;
    }

    // -------------------------------------------------------
    // INNER CLASS - risultato dry-run
    // -------------------------------------------------------

    public static class SapDryRunResult {

        public enum Stato { OK, WARNING, ERRORE }

        private final Stato  stato;
        private final String dettaglio;

        private SapDryRunResult(Stato stato, String dettaglio) {
            this.stato     = stato;
            this.dettaglio = dettaglio;
        }

        public static SapDryRunResult ok(String dettaglio) {
            return new SapDryRunResult(Stato.OK, dettaglio);
        }
        public static SapDryRunResult warning(String dettaglio) {
            return new SapDryRunResult(Stato.WARNING, dettaglio);
        }
        public static SapDryRunResult error(String dettaglio) {
            return new SapDryRunResult(Stato.ERRORE, dettaglio);
        }

        public Stato  getStato()     { return stato; }
        public String getDettaglio() { return dettaglio; }
        public boolean isOk()        { return stato == Stato.OK; }
        public boolean isError()     { return stato == Stato.ERRORE; }

        public String getEsitoIcona() {
            switch (stato) {
                case OK:      return "\u2705 " + dettaglio;
                case WARNING: return "\u26A0\uFE0F " + dettaglio;
                case ERRORE:  return "\u274C " + dettaglio;
                default:      return dettaglio;
            }
        }
    }
}
