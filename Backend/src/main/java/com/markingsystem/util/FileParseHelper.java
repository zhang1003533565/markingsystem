package com.markingsystem.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 文件解析工具类
 * 统一解析 Excel 和 CSV 文件
 */
@Component
public class FileParseHelper {

    /**
     * 解析文件数据
     * @param file 上传的文件
     * @return 解析后的文件数据
     * @throws IOException 文件读取异常
     */
    public ParsedFileData parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new IOException("文件名不能为空");
        }

        // 根据文件扩展名判断类型
        if (filename.toLowerCase().endsWith(".xlsx") || filename.toLowerCase().endsWith(".xls")) {
            return parseExcel(file);
        } else if (filename.toLowerCase().endsWith(".csv")) {
            return parseCsv(file);
        } else {
            throw new IOException("不支持的文件类型，只支持 Excel (.xlsx, .xls) 和 CSV (.csv) 文件");
        }
    }

    /**
     * 解析 Excel 文件
     * @param file Excel 文件
     * @return 解析后的文件数据
     * @throws IOException 文件读取异常
     */
    private ParsedFileData parseExcel(MultipartFile file) throws IOException {
        List<String> headers = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            
            // 只处理第一个 sheet
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IOException("Excel 文件中没有工作表");
            }

            // 解析表头（第一行）
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Excel 文件中没有表头行");
            }

            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                String value = getCellValueAsString(cell);
                headers.add(value != null ? value.trim() : "");
            }

            // 解析数据行（从第二行开始）
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                List<Object> rowData = new ArrayList<>();
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.getCell(j);
                    rowData.add(getCellValue(cell));
                }
                rows.add(rowData);
            }
        }

        return new ParsedFileData(headers, rows, file.getOriginalFilename());
    }

    /**
     * 解析 CSV 文件
     * @param file CSV 文件
     * @return 解析后的文件数据
     * @throws IOException 文件读取异常
     */
    private ParsedFileData parseCsv(MultipartFile file) throws IOException {
        List<String> headers = new ArrayList<>();
        List<List<Object>> rows = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            
            String line;
            boolean isFirstLine = true;
            
            while ((line = reader.readLine()) != null) {
                // 跳过空行
                if (line.trim().isEmpty()) continue;

                // 处理 BOM
                if (isFirstLine && line.startsWith("\uFEFF")) {
                    line = line.substring(1);
                }

                // 解析 CSV 行（简单实现，不考虑引号内的逗号）
                String[] values = line.split(",");
                
                if (isFirstLine) {
                    // 表头行
                    for (String value : values) {
                        headers.add(value.trim());
                    }
                    isFirstLine = false;
                } else {
                    // 数据行
                    List<Object> rowData = new ArrayList<>();
                    for (String value : values) {
                        rowData.add(value.trim());
                    }
                    // 补齐缺失的列
                    while (rowData.size() < headers.size()) {
                        rowData.add("");
                    }
                    rows.add(rowData);
                }
            }
        }

        if (headers.isEmpty()) {
            throw new IOException("CSV 文件中没有表头行");
        }

        return new ParsedFileData(headers, rows, file.getOriginalFilename());
    }

    /**
     * 获取单元格的值
     * @param cell 单元格
     * @return 单元格的值
     */
    private Object getCellValue(Cell cell) {
        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue();
                }
                return cell.getNumericCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }

    /**
     * 获取单元格的字符串值
     * @param cell 单元格
     * @return 单元格的字符串值
     */
    private String getCellValueAsString(Cell cell) {
        Object value = getCellValue(cell);
        if (value == null) return "";
        return value.toString();
    }

    /**
     * 解析后的文件数据类
     */
    public static class ParsedFileData {
        private final List<String> headers;
        private final List<List<Object>> rows;
        private final String filename;

        public ParsedFileData(List<String> headers, List<List<Object>> rows, String filename) {
            this.headers = headers;
            this.rows = rows;
            this.filename = filename;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public List<List<Object>> getRows() {
            return rows;
        }

        public String getFilename() {
            return filename;
        }

        /**
         * 获取表头索引映射
         * @param headerName 表头名称
         * @return 表头索引，不存在返回 -1
         */
        public int getHeaderIndex(String headerName) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).equalsIgnoreCase(headerName)) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * 检查是否包含指定表头
         * @param headerName 表头名称
         * @return 是否包含
         */
        public boolean hasHeader(String headerName) {
            return getHeaderIndex(headerName) >= 0;
        }
    }

    /**
     * 解析成绩值
     * @param value 成绩值对象
     * @return 解析后的 Double 值，解析失败返回 null
     */
    public static Double parseScore(Object value) {
        if (value == null) return null;

        String strValue = value.toString().trim();
        if (strValue.isEmpty()) return null;

        try {
            return Double.parseDouble(strValue);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取字符串值
     * @param value 对象值
     * @return 字符串值，null 返回空字符串
     */
    public static String getStringValue(Object value) {
        if (value == null) return "";
        return value.toString().trim();
    }

    public List<String> collectDistinctColumnValues(ParsedFileData fileData, List<String> candidateHeaders) {
        if (fileData == null || candidateHeaders == null || candidateHeaders.isEmpty()) {
            return List.of();
        }
        int headerIndex = -1;
        for (String header : candidateHeaders) {
            if (header != null && !header.isBlank() && fileData.hasHeader(header)) {
                headerIndex = fileData.getHeaderIndex(header);
                break;
            }
        }
        if (headerIndex < 0) {
            return List.of();
        }

        Set<String> values = new LinkedHashSet<>();
        for (List<Object> row : fileData.getRows()) {
            if (row == null || headerIndex >= row.size()) {
                continue;
            }
            String value = getStringValue(row.get(headerIndex));
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return new ArrayList<>(values);
    }

    public java.util.Map<String, List<String>> collectDistinctColumnValuesByHeaders(ParsedFileData fileData, List<String> candidateHeaders) {
        java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        if (fileData == null || candidateHeaders == null || candidateHeaders.isEmpty()) {
            return result;
        }
        for (String header : candidateHeaders) {
            if (header == null || header.isBlank() || !fileData.hasHeader(header)) {
                continue;
            }
            result.put(header, collectDistinctColumnValues(fileData, List.of(header)));
        }
        return result;
    }

    public java.util.Map<String, List<String>> collectDistinctColumnValuesByHeaders(ParsedFileData fileData) {
        java.util.Map<String, List<String>> result = new java.util.LinkedHashMap<>();
        if (fileData == null || fileData.getHeaders() == null || fileData.getHeaders().isEmpty()) {
            return result;
        }
        for (String header : fileData.getHeaders()) {
            if (header == null || header.isBlank()) {
                continue;
            }
            result.put(header, collectDistinctColumnValues(fileData, List.of(header)));
        }
        return result;
    }
}
