package lamplast.utility.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.fasterxml.jackson.databind.ObjectMapper;
import lamplast.utility.model.ScheduleLineData;

/**
 * Converte file Excel (.xlsx e .xls) in oggetti ScheduleLineData.
 *
 * Supporta:
 *  - Formato .xlsx  (Office 2007+)  →  XSSFWorkbook
 *  - Formato .xls   (Office 97-2003) → HSSFWorkbook
 *
 * Il foglio da leggere viene cercato per nome (es. "Export"); se non
 * trovato si usa il primo foglio come fallback.
 * Il nome del foglio è configurabile tramite ColumnMapping.sheetName.
 */
public class ExcelParser {

    private final ColumnMapping columnMapping;
    private final DateTimeFormatter dateFormatter;

    public ExcelParser(ColumnMapping columnMapping) {
        this.columnMapping = columnMapping;
        this.dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    }

    // -------------------------------------------------------
    // ENTRY POINT PUBBLICO
    // -------------------------------------------------------

    /**
     * Parsing dei dati Excel.
     *
     * @param excelBytes  contenuto grezzo del file (xls o xlsx)
     * @param fileName    nome originale del file (usato solo per
     *                    determinare il formato; es. "piano.xls")
     */
    public List<ScheduleLineData> parseExcel(byte[] excelBytes,
                                             String fileName) throws Exception {

        List<ScheduleLineData> result = new ArrayList<>();

        try (Workbook workbook = openWorkbook(excelBytes, fileName)) {

            Sheet sheet = resolveSheet(workbook);

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new Exception("Nessuna riga di intestazione trovata nel foglio \""
                        + sheet.getSheetName() + "\"");
            }

            Map<Integer, String> columnIndexes = buildColumnIndex(headerRow);

            // Parsing righe dati
            int emptyRowStreak = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);

                if (isRowEmpty(row)) {
                    emptyRowStreak++;
                    if (emptyRowStreak >= 1) {
                        break; // fine dati
                    }
                    continue;
                }

                emptyRowStreak = 0;

                ScheduleLineData data = parseRow(row, columnIndexes);
                if (data != null) {
                    result.add(data);
                }
            }
        }

        return result;
    }

    /**
     * Overload di compatibilità: usa fileName vuoto → default a xlsx.
     * Chiamare preferibilmente la versione con fileName.
     */
    public List<ScheduleLineData> parseExcel(byte[] excelBytes) throws Exception {
        return parseExcel(excelBytes, "file.xlsx");
    }

    // -------------------------------------------------------
    // APERTURA WORKBOOK (auto-detect formato)
    // -------------------------------------------------------

    /**
     * Apre il workbook scegliendo automaticamente il parser corretto.
     *
     * Logica di rilevamento:
     *  1. Se il fileName termina con ".xls" (case-insensitive) → HSSF
     *  2. Altrimenti (xlsx, xlsm, nome sconosciuto) → XSSF
     *
     * Nota: i magic bytes dei due formati sono distinti ma Apache POI
     * lancia eccezioni chiare se si usa il parser sbagliato, quindi
     * il fallback nel catch è un'ulteriore rete di sicurezza.
     */
    /**
     * Rileva il formato dal primo byte del file (magic bytes):
     *   0xD0 0xCF → OLE2 compound document → XLS  → HSSFWorkbook
     *   0x50 0x4B → ZIP (PK)              → XLSX → XSSFWorkbook
     *
     * Questo approccio è affidabile indipendentemente dall'estensione
     * o dal nome del file trasmesso dal browser.
     */
    private Workbook openWorkbook(byte[] bytes, String fileName) throws Exception {

        if (bytes == null || bytes.length < 2) {
            throw new Exception("File vuoto o non leggibile.");
        }

        // Magic bytes: OLE2 = D0 CF, ZIP/OOXML = 50 4B
        boolean isOle2 = ((bytes[0] & 0xFF) == 0xD0) && ((bytes[1] & 0xFF) == 0xCF);

        if (isOle2) {
            // Formato OLE2 → XLS legacy (Office 97-2003)
            return new HSSFWorkbook(new ByteArrayInputStream(bytes));
        } else {
            // Formato ZIP/OOXML → XLSX (Office 2007+)
            return new XSSFWorkbook(new ByteArrayInputStream(bytes));
        }
    }

    // -------------------------------------------------------
    // RISOLUZIONE FOGLIO
    // -------------------------------------------------------

    /**
     * Cerca il foglio per nome configurato; se non esiste usa il primo.
     * Registra l'eventuale fallback nel campo lastSheetResolutionNote
     * (utile per il log UI).
     */
    private String lastSheetNote = null;

    private Sheet resolveSheet(Workbook wb) {

        String targetName = (columnMapping.sheetName != null
                && !columnMapping.sheetName.isBlank())
                ? columnMapping.sheetName.trim()
                : "Export";

        Sheet sheet = wb.getSheet(targetName);

        if (sheet != null) {
            lastSheetNote = "Foglio \"" + sheet.getSheetName() + "\" trovato.";
            return sheet;
        }

        // Fallback: primo foglio disponibile
        sheet = wb.getSheetAt(0);
        lastSheetNote = "Foglio \"" + targetName + "\" non trovato — "
                + "uso del primo foglio disponibile: \""
                + sheet.getSheetName() + "\".";
        return sheet;
    }

    /** Nota sull'ultimo foglio risolto (per visualizzazione nel log UI). */
    public String getLastSheetNote() {
        return lastSheetNote;
    }

    // -------------------------------------------------------
    // PARSING RIGHE
    // -------------------------------------------------------

    private ScheduleLineData parseRow(Row row, Map<Integer, String> columns) {

        ScheduleLineData data = new ScheduleLineData();
        DataFormatter formatter = new DataFormatter();

        for (Map.Entry<Integer, String> entry : columns.entrySet()) {
            int colIndex   = entry.getKey();
            String colName = entry.getValue();

            Cell cell = row.getCell(colIndex);
            if (cell == null) continue;

            String value = getCellValue(cell, formatter);

            if (colName.equalsIgnoreCase(columnMapping.orderColumn)) {
                data.setOrderNumber(value);
            } else if (colName.equalsIgnoreCase(columnMapping.itemColumn)) {
                data.setItemNumber(parseInteger(value));
            } else if (colName.equalsIgnoreCase(columnMapping.scheduleColumn)) {
                data.setScheduleLine(parseInteger(value));
            } else if (colName.equalsIgnoreCase(columnMapping.materialColumn)) {
                data.setMaterial(value);
            } else if (colName.equalsIgnoreCase(columnMapping.materialTextColumn)) {
                data.setMaterialText(value);
            } else if (colName.equalsIgnoreCase(columnMapping.quantityColumn)) {
                data.setQuantity(value);
            } else if (colName.equalsIgnoreCase(columnMapping.dateColumn)) {
                data.setProductionDate(parseDate(value));
            }
        }

        return data;
    }

    // -------------------------------------------------------
    // UTILITY
    // -------------------------------------------------------

    private Map<Integer, String> buildColumnIndex(Row headerRow) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                String header = cell.toString().trim();
                if (!header.isEmpty()) {
                    map.put(c, header);
                }
            }
        }
        return map;
    }

    private String getCellValue(Cell cell, DataFormatter formatter) {
        if (cell.getCellType() == CellType.NUMERIC
                && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
            return date.format(dateFormatter);
        }
        return formatter.formatCellValue(cell);
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            // Gestisce sia "10" che "10.0" (celle numeriche formattate)
            return Double.valueOf(value.replace(",", ".")).intValue();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value, dateFormatter);
        } catch (Exception e) {
            return null;
        }
    }

    /** Converte lista in JSON (per debug/visualizzazione). */
    public String toJson(List<ScheduleLineData> data) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // per LocalDate
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    }

    // -------------------------------------------------------
    // CONFIGURAZIONE NOMI COLONNE + FOGLIO
    // -------------------------------------------------------

    /**
     * Configurazione nomi colonne Excel e nome foglio.
     * I default corrispondono alle property di config.properties.
     */
    public static class ColumnMapping {

        /** Nome del foglio da leggere (default "Export"). */
        public String sheetName          = "Export";

        public String orderColumn        = "Ordine";
        public String itemColumn         = "Pos.";
        public String scheduleColumn     = "Sch.";
        public String materialColumn     = "Materiale";
        public String materialTextColumn = "Text";
        public String quantityColumn     = "Qtà";
        public String dateColumn         = "Data prod.";
    }
}
