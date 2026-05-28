package com.eone.fcs.repository;

import com.eone.fcs.model.SyncRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Repository per la tabella tabfcssync.
 *
 * Gestisce la lettura e l'aggiornamento dello stato di sincronizzazione delta.
 * Usa la stessa {@link Connection} passata nel costruttore (non la chiude).
 *
 * Pattern di utilizzo nel bridge (modalità delta):
 * <pre>
 *   SyncRecord rec = syncRepo.load("MARA");
 *   syncRepo.markRunning("MARA");
 *   try {
 *       // ... fetch da S/4H con filtro rec.nextSyncFrom() ...
 *       // ... upsert su Postgres ...
 *       syncRepo.markOk("MARA", count, upserted, durationMs);
 *   } catch (Exception e) {
 *       syncRepo.markError("MARA", e.getMessage());
 *       throw e;
 *   }
 * </pre>
 *
 * Nota sul campo RUNNING: serve come guardia anti-overlap rudimentale.
 * Se al prossimo run la riga è ancora RUNNING (run precedente crashato),
 * il bridge la tratta come ERROR e riprende dall'ultimo last_sync valido.
 */
public class SyncRepository {

    private static final Logger log = LoggerFactory.getLogger(SyncRepository.class);

    private final Connection conn;
    private final String     tenant;

    public SyncRepository(Connection conn, String tenant) {
        this.conn   = conn;
        this.tenant = tenant;
    }

    // -------------------------------------------------------------------------
    // Lettura
    // -------------------------------------------------------------------------

    /**
     * Carica il record di sincronizzazione per l'entità indicata.
     *
     * @param entity codice entità (MARA, LFA1, KNA1, EKET, VBEP)
     * @return il SyncRecord corrente
     * @throws SQLException se il record non esiste o errore DB
     */
    public SyncRecord load(String entity) throws SQLException {
        String sql = """
                SELECT entity, last_sync, next_sync_from, last_status
                  FROM public.tabfcssync
                 WHERE tenant = ? AND entity = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, entity);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException(
                        "Record tabfcssync non trovato per tenant='" + tenant +
                        "' entity='" + entity + "'. Eseguire step1_tabfcssync.sql.");
                }
                OffsetDateTime lastSync     = toOffsetDateTime(rs.getTimestamp("last_sync"));
                OffsetDateTime nextSyncFrom = toOffsetDateTime(rs.getTimestamp("next_sync_from"));
                String         status       = rs.getString("last_status");

                // Guardia anti-overlap: se era RUNNING il processo precedente è crashato.
                // Trattiamo come ERROR: il prossimo run riprenderà dall'ultimo last_sync valido.
                if ("RUNNING".equals(status)) {
                    log.warn("[sync-{}] Record in stato RUNNING (processo precedente crashato?). " +
                             "Riprendo dall'ultimo last_sync valido.", entity);
                }

                SyncRecord rec = new SyncRecord(tenant, entity, lastSync, nextSyncFrom, status);
                log.info("[sync-{}] Stato corrente: status={}, last_sync={}, next_sync_from={}",
                         entity, status,
                         lastSync     != null ? lastSync.toString()     : "mai",
                         nextSyncFrom != null ? nextSyncFrom.toString() : "mai");
                return rec;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Aggiornamento stato
    // -------------------------------------------------------------------------

    /**
     * Imposta lo stato a RUNNING prima di avviare il run delta.
     * Aggiorna also updated_at.
     */
    public void markRunning(String entity) throws SQLException {
        String sql = """
                UPDATE public.tabfcssync
                   SET last_status  = 'RUNNING',
                       updated_at   = now()
                 WHERE tenant = ? AND entity = ?
                """;
        execute(sql, entity);
        conn.commit();
        log.debug("[sync-{}] Stato → RUNNING", entity);
    }

    /**
     * Imposta lo stato a OK dopo un run delta completato con successo.
     * Aggiorna last_sync, next_sync_from e le statistiche.
     *
     * @param entity     codice entità
     * @param count      record trovati modificati in S/4H
     * @param upserted   record scritti su Postgres
     * @param durationMs durata del run in millisecondi
     */
    public void markOk(String entity, int count, int upserted, long durationMs)
            throws SQLException {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String sql = """
                UPDATE public.tabfcssync
                   SET last_status      = 'OK',
                       last_sync        = ?,
                       next_sync_from   = ?,
                       last_count       = ?,
                       last_upserted    = ?,
                       last_duration_ms = ?,
                       last_error       = NULL,
                       updated_at       = now()
                 WHERE tenant = ? AND entity = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now.toInstant()));
            ps.setTimestamp(2, Timestamp.from(now.toInstant()));
            ps.setInt      (3, count);
            ps.setInt      (4, upserted);
            ps.setLong     (5, durationMs);
            ps.setString   (6, tenant);
            ps.setString   (7, entity);
            ps.executeUpdate();
        }
        conn.commit();
        log.info("[sync-{}] Stato → OK | trovati={} upsertati={} durata={}ms last_sync={}",
                 entity, count, upserted, durationMs, now);
    }

    /**
     * Imposta lo stato a ERROR senza aggiornare last_sync.
     * Il prossimo run riprenderà dall'ultimo last_sync valido.
     *
     * @param entity       codice entità
     * @param errorMessage messaggio di errore da salvare
     */
    public void markError(String entity, String errorMessage) {
        String sql = """
                UPDATE public.tabfcssync
                   SET last_status = 'ERROR',
                       last_error  = ?,
                       updated_at  = now()
                 WHERE tenant = ? AND entity = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, errorMessage != null
                            ? errorMessage.substring(0, Math.min(errorMessage.length(), 2000))
                            : "Errore sconosciuto");
            ps.setString(2, tenant);
            ps.setString(3, entity);
            ps.executeUpdate();
            conn.commit();
            log.warn("[sync-{}] Stato → ERROR: {}", entity, errorMessage);
        } catch (SQLException ex) {
            // Non rilanciamo: siamo già in un handler di errore
            log.error("[sync-{}] Impossibile aggiornare stato ERROR su tabfcssync: {}",
                      entity, ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private void execute(String sql, String entity) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tenant);
            ps.setString(2, entity);
            ps.executeUpdate();
        }
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp ts) {
        if (ts == null) return null;
        return ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
