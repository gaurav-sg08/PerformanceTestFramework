package com.performanceTest.Inputreader;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.util.*;

public class ExcelReader {

    public static Map<String, List<Map<String, String>>> readTestSteps(String filePath) {
        Map<String, List<Map<String, String>>> stepsMap = new LinkedHashMap<>();
        List<Map<String, String>> loginSteps = new ArrayList<>();
        List<Map<String, String>> testSteps = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(filePath);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            int colCount = headerRow.getPhysicalNumberOfCells();

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, String> rowData = new LinkedHashMap<>();

                for (int j = 0; j < colCount; j++) {
                    Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                    String key = headerRow.getCell(j).getStringCellValue().trim();
                    String value = getCellValueAsString(cell).trim();
                    rowData.put(key, value);
                }

                String name = rowData.get("name").trim();
                if ("loginSteps".equalsIgnoreCase(name)) {
                    loginSteps.add(rowData);
                } else if (!name.isEmpty()) {
                    testSteps.add(rowData);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        stepsMap.put("loginSteps", loginSteps);
        stepsMap.put("testSteps", testSteps);
        return stepsMap;
    }

    private static String getCellValueAsString(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            case BLANK:
                return "";
            default:
                return cell.toString();
        }
    }
}
