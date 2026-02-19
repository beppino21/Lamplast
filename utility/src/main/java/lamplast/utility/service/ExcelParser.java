package lamplast.utility.service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Converte file Excel in oggetti ScheduleLineData
 */
public class ExcelParser {
    
    private final ColumnMapping columnMapping;
    private final DateTimeFormatter dateFormatter;
    
    public ExcelParser(ColumnMapping columnMapping) {
        this.columnMapping = columnMapping;
        this.dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    }
    
    /**
     * Parsing dei dati Excel
     */
    public List<ScheduleLineData> parseExcel(byte[] excelBytes) throws Exception {
        
        List<ScheduleLineData> result = new ArrayList<>();
        
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            
            // Prende il primo foglio (assumo "Export")
            Sheet sheet = workbook.getSheetAt(0);
            
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new Exception("Nessuna riga di intestazione trovata");
            }
            
            // Mappa indice colonna -> nome colonna
            Map<Integer, String> columnIndexes = buildColumnIndex(headerRow);
            
            // Parsing righe dati
            int emptyRowStreak = 0;
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                
                if (isRowEmpty(row)) {
                    emptyRowStreak++;
                    if (emptyRowStreak >= 1) {
                        break; // Fine dati
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
     * Parsing di una singola riga
     */
    private ScheduleLineData parseRow(Row row, Map<Integer, String> columns) {
        
        ScheduleLineData data = new ScheduleLineData();
        DataFormatter formatter = new DataFormatter();
        
        for (Map.Entry<Integer, String> entry : columns.entrySet()) {
            int colIndex = entry.getKey();
            String columnName = entry.getValue();
            
            Cell cell = row.getCell(colIndex);
            if (cell == null) continue;
            
            String value = getCellValue(cell, formatter);
            
            // Mapping valori
            if (columnName.equalsIgnoreCase(columnMapping.orderColumn)) {
                data.setOrderNumber(value);
            } 
            else if (columnName.equalsIgnoreCase(columnMapping.itemColumn)) {
                data.setItemNumber(parseInteger(value));
            }
            else if (columnName.equalsIgnoreCase(columnMapping.scheduleColumn)) {
                data.setScheduleLine(parseInteger(value));
            }
            else if (columnName.equalsIgnoreCase(columnMapping.materialColumn)) {
                data.setMaterial(value);
            }
            else if (columnName.equalsIgnoreCase(columnMapping.materialTextColumn)) {
                data.setMaterialText(value);
            }
            else if (columnName.equalsIgnoreCase(columnMapping.quantityColumn)) {
                data.setQuantity(value);
            }
            else if (columnName.equalsIgnoreCase(columnMapping.dateColumn)) {
                data.setProductionDate(parseDate(value));
            }
        }
        
        return data;
    }
    
    /**
     * Crea mappa indice -> nome colonna
     */
    private Map<Integer, String> buildColumnIndex(Row headerRow) {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (int c = 0; c < headerRow.getLastCellNum(); c++) {
            Cell cell = headerRow.getCell(c);
            if (cell != null) {
                map.put(c, cell.toString().trim());
            }
        }
        return map;
    }
    
    /**
     * Estrae valore cella come stringa
     */
    private String getCellValue(Cell cell, DataFormatter formatter) {
        if (cell.getCellType() == CellType.NUMERIC 
            && org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
            LocalDate date = cell.getLocalDateTimeCellValue().toLocalDate();
            return date.format(dateFormatter);
        }
        return formatter.formatCellValue(cell);
    }
    
    /**
     * Verifica se la riga è vuota
     */
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
        try {
            return Double.valueOf(value).intValue();
        } catch (Exception e) {
            return null;
        }
    }
    
    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, dateFormatter);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Converte lista in JSON (per visualizzazione)
     */
    public String toJson(List<ScheduleLineData> data) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules(); // Per LocalDate
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
    }
    
    /**
     * Configurazione nomi colonne Excel
     */
    public static class ColumnMapping {
        public String orderColumn = "Ordine";
        public String itemColumn = "Pos.";
        public String scheduleColumn = "Sch.";
        public String materialColumn = "Materiale";
        public String materialTextColumn = "Text";
        public String quantityColumn = "Qtà";
        public String dateColumn = "Data prod.";
    }
}