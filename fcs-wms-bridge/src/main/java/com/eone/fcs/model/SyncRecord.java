package com.eone.fcs.model;

import java.time.OffsetDateTime;

/**
 * Rappresenta un record della tabella tabfcssync.
 *
 * Usato da {@link com.eone.fcs.repository.SyncRepository} per leggere
 * e aggiornare lo stato della sincronizzazione delta per entità.
 *
 * @param tenant        Identificatore tenant
 * @param entity        Codice entità: MARA | LFA1 | KNA1 | EKET | VBEP
 * @param lastSync      Timestamp ultimo run OK (null se mai eseguito)
 * @param nextSyncFrom  Timestamp da usare come filtro al prossimo run
 * @param lastStatus    Stato ultimo run: INIT | OK | ERROR | RUNNING
 */
public record SyncRecord(
        String         tenant,
        String         entity,
        OffsetDateTime lastSync,
        OffsetDateTime nextSyncFrom,
        String         lastStatus
) {
    /** Restituisce true se il record non è mai stato sincronizzato con successo. */
    public boolean isFirstRun() {
        return lastSync == null || "INIT".equals(lastStatus);
    }
}
