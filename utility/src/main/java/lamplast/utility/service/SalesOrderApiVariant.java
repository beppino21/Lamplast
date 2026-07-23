package lamplast.utility.service;

import lamplast.utility.config.SapConfiguration;

/**
 * Le due varianti dell'API OData su cui il progetto può dover operare,
 * a seconda della categoria documento SAP (VBTYP) dell'ordine:
 *
 *  - STANDARD:        API_SALES_ORDER_SRV               — SDDocumentCategory 'C' (ordini di vendita standard)
 *  - WITHOUT_CHARGE:   API_SALES_ORDER_WITHOUT_CHARGE_SRV — SDDocumentCategory 'I' (ordini senza addebito / gratuiti)
 *
 * Riferimento SAP: KBA 3621002 ("Missing Sales Orders in CDS View
 * I_SALESORDER...") e KBA 2752419 ("OData API service for order type CBFD").
 *
 * Entity set e nomi dei campi chiave sotto sono stati CONFERMATI contro il
 * $metadata reale del servizio API_SALES_ORDER_WITHOUT_CHARGE_SRV.
 *
 * LIMITE IMPORTANTE, CONFERMATO DAL $metadata:
 * l'entity set "A_SlsOrdWthoutChrgSchedLine" (schedulazioni degli ordini
 * senza addebito) è dichiarato sap:creatable="false" sap:updatable="false"
 * sap:deletable="false" — è SOLA LETTURA. Non espone inoltre i campi
 * RequestedDeliveryDate / ScheduleLineOrderQuantity presenti invece su
 * A_SalesOrderScheduleLine (variante STANDARD). Di conseguenza, per gli
 * ordini di categoria 'I' questa API NON permette di inserire o modificare
 * schedulazioni via OData: SapScheduleLineService intercetta questo caso e
 * lo segnala come "non gestibile automaticamente" invece di tentare una
 * scrittura destinata comunque a essere respinta da SAP. Vedi la
 * discussione in chat per le opzioni valutate (BAPI/RFC dedicata, gestione
 * manuale in VA02, eventuale estensione custom lato SAP).
 */
public enum SalesOrderApiVariant {

    STANDARD(
        "A_SalesOrderItem",
        "A_SalesOrderScheduleLine",
        "SalesOrder",
        "SalesOrderItem"
    ),

    WITHOUT_CHARGE(
        "A_SalesOrderWithoutChargeItem",
        "A_SlsOrdWthoutChrgSchedLine",     // DA VERIFICARE su $metadata
        "SalesOrderWithoutCharge",
        "SalesOrderWithoutChargeItem"
    );

    /** Entity set delle posizioni ordine (per il controllo materiale). */
    public final String itemEntitySet;

    /** Entity set delle schedulazioni. */
    public final String scheduleLineEntitySet;

    /** Nome del campo chiave "numero ordine" in questa variante. */
    public final String orderKeyField;

    /** Nome del campo chiave "numero posizione" in questa variante. */
    public final String itemKeyField;

    SalesOrderApiVariant(String itemEntitySet, String scheduleLineEntitySet,
                          String orderKeyField, String itemKeyField) {
        this.itemEntitySet         = itemEntitySet;
        this.scheduleLineEntitySet = scheduleLineEntitySet;
        this.orderKeyField         = orderKeyField;
        this.itemKeyField          = itemKeyField;
    }

    public String baseUrl(SapConfiguration config) {
        return this == STANDARD
            ? config.getSalesOrderApiUrl()
            : config.getSalesOrderWithoutChargeApiUrl();
    }

    /** La "variante alternativa", usata per il fallback automatico. */
    public SalesOrderApiVariant other() {
        return this == STANDARD ? WITHOUT_CHARGE : STANDARD;
    }
}
