package com.centralizesys.service;

import com.centralizesys.exception.BusinessRuleException;
import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.model.dto.BackupFileDTO;
import com.centralizesys.model.product.Product;
import com.centralizesys.model.sales.Venta;
import com.centralizesys.repository.AuditoriaRepository;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.security.SecurityUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

@Service
// TODO: Technical Debt - Extract ProcessBuilder OS executions (pg_dump/psql) into a dedicated
// Infrastructure/CommandExecutor component. This will decouple business logic from OS-level
// execution and allow proper unit testing.
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final DateTimeFormatter FMT_FILE = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");

    private static final String EXT_XLSX = ".xlsx";
    private static final String PREFIX_FILE = "centralizesys_";
    @SuppressWarnings("java:S2068")
    private static final String ENV_PG_PASS = "PGPASSWORD";

    private static final Long RETENTION_DAYS_DAILY = 60L;
    private static final String FECHA = "Fecha";
    private static final String BACKUP_CLEANUP = "BACKUP_CLEANUP";

    private final JdbcTemplate jdbcTemplate;
    private final AuditoriaService auditoriaService;
    private final ProductRepository productRepository;
    private final VentaRepository ventaRepository;
    private final CompraRepository compraRepository;
    private final DeudoresRepository deudoresRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final BackupPathStrategy pathStrategy;

    private final String dbUrl;
    private final String dbUser;
    private final String dbPassword;

    public BackupService(JdbcTemplate jdbcTemplate,
                         AuditoriaService auditoriaService,
                         ProductRepository productRepository,
                         VentaRepository ventaRepository,
                         CompraRepository compraRepository,
                         DeudoresRepository deudoresRepository,
                         AuditoriaRepository auditoriaRepository,
                         BackupPathStrategy pathStrategy,
                         @Value("${spring.datasource.url}") String dbUrl,
                         @Value("${spring.datasource.username}") String dbUser,
                         @Value("${spring.datasource.password}") String dbPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditoriaService = auditoriaService;
        this.productRepository = productRepository;
        this.ventaRepository = ventaRepository;
        this.compraRepository = compraRepository;
        this.deudoresRepository = deudoresRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.pathStrategy = pathStrategy;
        this.dbUrl = dbUrl;
        this.dbUser = dbUser;
        this.dbPassword = dbPassword;
    }


    public enum BackupType {
        DAILY,
        MANUAL;

        public String getDirectory(BackupPathStrategy strategy) {
            return switch (this) {
                case DAILY -> strategy.getDailyDir();
                case MANUAL -> strategy.getManualDir();
            };
        }
    }

    public void performBackup(BackupType type) {
        performBackup(type, SecurityUtils.getAuthenticatedUserId());
    }

    public void performBackup(BackupType type, Long userId) {
        // Fallback or System User if null passed (though unlikely if called correctly)
        if (userId == null)
            userId = 0L;

        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        String timestamp = now.format(FMT_FILE);
        String prefix = type == BackupType.DAILY ? "daily_" : "manual_";

        String excelFileName = PREFIX_FILE + prefix + timestamp + EXT_XLSX;

        Path dirPath = Paths.get(type.getDirectory(pathStrategy));

        try {
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }

            Path fullExcelPath = dirPath.resolve(excelFileName).toAbsolutePath();

            // 1. PostgreSQL DB Backup
            String sqlFileName = PREFIX_FILE + prefix + timestamp + ".sql";
            Path fullSqlPath = dirPath.resolve(sqlFileName).toAbsolutePath();
            performPgDump(fullSqlPath);

            // 2. Excel Export
            exportToExcel(fullExcelPath.toString());

            // 3. Audit Success
            String message = String.format("Respaldo %s (Excel) completo.", type.name());
            auditoriaService.registrarAccion(userId, "BACKUP_EXITOSO", message);

            if (type == BackupType.MANUAL) {
                cleanupManualBackups();
            }

        } catch (IOException | DataAccessException | IllegalArgumentException e) {
            auditoriaService.registrarAccion(userId, "BACKUP_FALLIDO", "Error: " + e.getMessage());
            throw new InfrastructureException("Backup failed: " + e.getMessage(), e);
        }
    }

    private void performPgDump(Path fullSqlPath) {
        try {
            executePgDump(fullSqlPath.toFile());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfrastructureException("Backup process interrupted", e);
        } catch (IOException e) {
            throw new InfrastructureException("IO Error during pg_dump", e);
        }
    }



    private void executePgDump(File outputFile) throws IOException, InterruptedException {
        String cleanUrl = dbUrl;
        if (cleanUrl.contains("?")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf('?'));
        }
        String cleanUriStr = cleanUrl.replace("jdbc:", "");
        java.net.URI uri = java.net.URI.create(cleanUriStr);
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String db = uri.getPath().replace("/", "");

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        List<String> command = new ArrayList<>();
        if (isWindows) {
            command.addAll(Arrays.asList("docker", "exec", "-i", "-e", ENV_PG_PASS, "centralizesys_postgres",
                    "pg_dump", "-U", dbUser, "-d", db, "--clean", "--if-exists", "-O", "-x"));
        } else {
            command.addAll(Arrays.asList("pg_dump", "--clean", "--if-exists", "-O", "-x",
                    "-U", dbUser, "-h", host, "-p", String.valueOf(port), "-d", db));
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put(ENV_PG_PASS, dbPassword);

        // Critical OS Buffer Fix: inheritIO for stdin/stderr, redirect stdout to file
        pb.inheritIO();
        pb.redirectOutput(outputFile);

        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            if (outputFile.exists()) {
                try {
                    Files.delete(outputFile.toPath());
                } catch (IOException ignored) {
                    // Ignored on failure
                }
            }
            throw new InfrastructureException("pg_dump process failed with exit code: " + exitCode);
        }
    }

    public void restoreDatabase(File sqlFile) {
        if (!com.centralizesys.config.MaintenanceInterceptor.isMaintenanceMode.compareAndSet(false, true)) {
            throw new BusinessRuleException("Una restauración ya está en progreso. Por favor, espere.");
        }
        try {
            log.info("Triggering pre-restore safety net backup.");
            performSafetyNetBackup();

            DataSource ds = jdbcTemplate.getDataSource();
            if (ds != null && ds.isWrapperFor(HikariDataSource.class)) {
                HikariDataSource hds = ds.unwrap(HikariDataSource.class);
                hds.close();
            }

            executePsqlRestore(sqlFile);

            // Success Exit
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                // We use halt(0) instead of exit(0) to bypass Spring's shutdown hooks
                // because we manually closed the DataSource and Spring will deadlock trying to close it again.
                Runtime.getRuntime().halt(0);
            });
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("Restore failed", e);
            // Failure Exit
            Thread.ofVirtual().start(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
                Runtime.getRuntime().halt(1);
            });
            throw new InfrastructureException("Restore failed: " + e.getMessage(), e);
        }
    }

    private void performSafetyNetBackup() {
        try {
            performBackup(BackupType.MANUAL, SecurityUtils.getAuthenticatedUserId());
        } catch (Exception e) {
            // Fail Fast constraint: If we can't secure current data, we DO NOT restore.
            throw new InfrastructureException("Safety net backup failed! Aborting restoration to prevent data loss.", e);
        }
    }

    private File preprocessSqlFile(File originalFile) throws IOException {
        java.nio.file.Path manualBackupDir = Paths.get(pathStrategy.getManualDir());
        if (!java.nio.file.Files.exists(manualBackupDir)) {
            java.nio.file.Files.createDirectories(manualBackupDir);
        }
        java.nio.file.Path tempPath = java.nio.file.Files.createTempFile(manualBackupDir, "backup_clean_", ".sql");
        File tempFile = tempPath.toFile();
        try (BufferedReader reader = new BufferedReader(new FileReader(originalFile));
             BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            writer.write("-- 1. Bulletproof Deadlock Prevention: Kill all other active connections (e.g., pgAdmin, hanging queries)");
            writer.newLine();
            writer.write("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = current_database() AND pid <> pg_backend_pid();");
            writer.newLine();
            writer.write("-- 2. Clean Wipe (Transactional DDL to prevent Hybrid Schema Drift)");
            writer.newLine();
            writer.write("DROP SCHEMA public CASCADE;");
            writer.newLine();
            writer.write("CREATE SCHEMA public;");
            writer.newLine();
            writer.write("GRANT ALL ON SCHEMA public TO public;");
            writer.newLine();

            String line;
            while ((line = reader.readLine()) != null) {
                // Ignore PostgreSQL 17 specific parameters that cause errors in PostgreSQL 16
                if (line.trim().startsWith("SET transaction_timeout")) {
                    continue;
                }
                writer.write(line);
                writer.newLine();
            }
        }
        return tempFile;
    }

    private void executePsqlRestore(File sqlFile) throws InfrastructureException, IOException, InterruptedException {
        File processedSqlFile = preprocessSqlFile(sqlFile);
        try {
            String cleanUrl = dbUrl;
            if (cleanUrl.contains("?")) {
                cleanUrl = cleanUrl.substring(0, cleanUrl.indexOf('?'));
            }
            String cleanUriStr = cleanUrl.replace("jdbc:", "");
            java.net.URI uri = java.net.URI.create(cleanUriStr);
            String db = uri.getPath().replace("/", "");

            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            List<String> command = new ArrayList<>();
            if (isWindows) {
                command.addAll(Arrays.asList("cmd.exe", "/c",
                        "docker exec -i -e " + ENV_PG_PASS + " centralizesys_postgres psql -v ON_ERROR_STOP=1 -U " + dbUser + " -d " + db + " --single-transaction < \"" + processedSqlFile.getAbsolutePath() + "\""));
            } else {
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                command.addAll(Arrays.asList("psql", "-v", "ON_ERROR_STOP=1", "-U", dbUser, "-h", host, "-p", String.valueOf(port), "-d", db, "--single-transaction"));
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.environment().put(ENV_PG_PASS, dbPassword);

            if (!isWindows) {
                pb.redirectInput(processedSqlFile);
            }
            pb.inheritIO();

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new InfrastructureException("psql process failed with exit code: " + exitCode);
            }
        } finally {
            if (processedSqlFile != null && processedSqlFile.exists() && !processedSqlFile.getAbsolutePath().equals(sqlFile.getAbsolutePath())) {
                try {
                    Files.delete(processedSqlFile.toPath());
                } catch (IOException e) {
                    log.warn("Could not delete temporary preprocessed SQL file: {}", processedSqlFile.getAbsolutePath(), e);
                }
            }
        }
    }

    private void exportToExcel(String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {

            // --- STYLES ---
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle oddRowStyle = workbook.createCellStyle();
            oddRowStyle.setWrapText(true);

            CellStyle evenRowStyle = workbook.createCellStyle();
            evenRowStyle.setWrapText(true);
            evenRowStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            evenRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("$#,##0.00"));
            currencyStyle.setWrapText(true);

            CellStyle currencyEvenStyle = workbook.createCellStyle();
            currencyEvenStyle.cloneStyleFrom(currencyStyle);
            currencyEvenStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            currencyEvenStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // --- SHEETS ---
            populateProductsSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populateSalesSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populatePurchasesSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populateDebtorsSheet(workbook, headerStyle, oddRowStyle, evenRowStyle, currencyStyle, currencyEvenStyle);
            populateStockSheet(workbook, headerStyle, oddRowStyle, evenRowStyle);
            populateAuditSheet(workbook, headerStyle, oddRowStyle, evenRowStyle);

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
        }
    }

    private void createHeader(Sheet sheet, String[] headers, CellStyle style) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void createNumericCell(Row row, int col, Double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : 0.0);
        cell.setCellStyle(style);
    }

    private void createTextCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value != null) {
            cell.setCellValue(value.toString());
        }
        cell.setCellStyle(style);
    }

    private String formatIsoDateForExcel(LocalDateTime dateTime) {
        if (dateTime == null)
            return "";
        try {
            return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd -- HH:mm:ss"));
        } catch (Exception e) {
            return dateTime.toString(); // Fallback
        }
    }

    // --- File Management APIs ---

    public List<com.centralizesys.model.dto.BackupFileDTO> listBackups() {
        List<BackupFileDTO> list = new ArrayList<>();
        list.addAll(scanDirectory(BackupType.DAILY));
        list.addAll(scanDirectory(BackupType.MANUAL));
        // Sort DESC
        list.sort((a, b) -> b.date().compareTo(a.date()));
        return list;
    }

    public File getBackupFile(String filename) throws FileNotFoundException {
        // Security: Prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        // Search in Daily
        Path dailyPath = Paths.get(BackupType.DAILY.getDirectory(pathStrategy), filename);
        if (Files.exists(dailyPath))
            return dailyPath.toFile();

        // Search in Manual
        Path manualPath = Paths.get(BackupType.MANUAL.getDirectory(pathStrategy), filename);
        if (Files.exists(manualPath))
            return manualPath.toFile();

        throw new FileNotFoundException("File not found: " + filename);
    }

    private List<BackupFileDTO> scanDirectory(BackupType type) {
        Path dirPath = Paths.get(type.getDirectory(pathStrategy));
        if (!Files.exists(dirPath))
            return Collections.emptyList();

        try (Stream<Path> stream = Files.list(dirPath)) {
            return stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        // Returns TRUE or FALSE based on filename extension. If true, path object
                        // is included in the resulting stream.
                        return name.endsWith(EXT_XLSX) || name.endsWith(".sql");
                    })
                    .map(path -> mapPathToDto(path, type))
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            log.error("Failed to scan directory: {}", type.name(), e);
            return Collections.emptyList();
        }
    }

    // Sonar: Extract complex lambda body
    private BackupFileDTO mapPathToDto(Path path, BackupType type) {
        String name = path.getFileName().toString();
        LocalDateTime date = parseDateFromFilename(name);
        if (date == null) {
            try {
                date = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()),
                        ZoneId.of("America/Argentina/Buenos_Aires"));
            } catch (IOException e) {
                date = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires")); // Fallback
            }
        }
        try {
            return new BackupFileDTO(
                    name,
                    date,
                    Files.size(path),
                    type.name() + (name.endsWith(".sql") ? "_SQL" : "_EXCEL"));
        } catch (IOException e) {
            return null;
        }
    }

    private LocalDateTime parseDateFromFilename(String name) {
        try {
            // Extract YYYYMMDD_HHmm
            int idx = name.lastIndexOf('_');
            if (idx == -1)
                return null;
            String timePart = name.substring(idx + 1, idx + 5);

            String temp = name.substring(0, idx);
            int idx2 = temp.lastIndexOf('_');
            if (idx2 == -1)
                return null;
            String datePart = temp.substring(idx2 + 1);

            return LocalDateTime.parse(datePart + "_" + timePart, FMT_FILE);
        } catch (Exception e) {
            return null;
        }
    }

    // --- Retention Logic (GFS Adapted) ---
    public void cleanupOldBackups() {
        Path dirPath = Paths.get(pathStrategy.getDailyDir());
        if (!Files.exists(dirPath))
            return;

        List<File> files;
        try (Stream<Path> stream = Files.list(dirPath)) {
            files = stream
                    .filter(p -> p.toString().endsWith(EXT_XLSX) || p.toString().endsWith(".sql"))
                    .map(Path::toFile)
                    .sorted((f1, f2) -> {
                        LocalDateTime d1 = parseDateFromFilename(f1.getName());
                        LocalDateTime d2 = parseDateFromFilename(f2.getName());
                        if (d1 == null && d2 == null) return Long.compare(f1.lastModified(), f2.lastModified());
                        if (d1 == null) return -1;
                        if (d2 == null) return 1;
                        return d1.compareTo(d2);
                    })
                    .toList();
        } catch (IOException e) {
            log.warn("Cleanup failed to list files", e);
            return;
        }

        processRetentionPolicy(files);
    }

    private void processRetentionPolicy(List<File> files) {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        Set<Integer> yearsKept = new HashSet<>();

        // Process files older than retention period
        List<File> filesToDelete = files.stream()
                .filter(f -> {
                    LocalDateTime fileDate = parseDateFromFilename(f.getName());
                    return fileDate != null && ChronoUnit.DAYS.between(fileDate.atZone(ZoneId.of("America/Argentina/Buenos_Aires")), now.atZone(ZoneId.of("America/Argentina/Buenos_Aires"))) > RETENTION_DAYS_DAILY;
                })

                .filter(f -> shouldDeleteArchivedFile(Objects.requireNonNull(parseDateFromFilename(f.getName())), now,
                        yearsKept))
                .toList();

        Long deletedCount = 0L;
        for (File f : filesToDelete) {
            try {
                Files.delete(f.toPath());
                deletedCount++;
            } catch (IOException e) {
                log.warn("Failed to delete old backup: {}", f.getName());
            }
        }

        if (deletedCount > 0) {
            auditoriaService.registrarAccion(0L, BACKUP_CLEANUP, "Deleted " + deletedCount + " old backups.");
        }
    }

    private boolean shouldDeleteArchivedFile(LocalDateTime fileDate, LocalDateTime now, Set<Integer> yearsKept) {
        int fileYear = fileDate.getYear();
        int currentYear = now.getYear();

        if (fileYear < (currentYear - 1)) {
            if (!yearsKept.contains(fileYear)) {
                yearsKept.add(fileYear); // Keep First
                return false;
            }
            return true; // Delete subsequent
        } else {
            // BI-MONTHLY (Current or Previous Year)
            int dom = fileDate.getDayOfMonth();

            // RETENTION STRATEGY (Bi-Monthly + First of Year):
            // 1. Always keep the first backup encountered for the year (Oldest).
            // 2. Otherwise, keep only 1st and 15th of the month.
            if (!yearsKept.contains(fileYear)) {
                yearsKept.add(fileYear); // Mark year as having a "Start of Year" candidate kept
                return false; // DO NOT DELETE (Keep as 'First of Year' replacement)
            }

            // Normal Bi-Monthly retention for the rest
            return dom != 1 && dom != 15;
        }
    }


    public void removeMidDayBackup() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Argentina/Buenos_Aires"));
        Path dir = Paths.get(pathStrategy.getDailyDir());
        if (!Files.exists(dir)) return;

        try (Stream<Path> stream = Files.list(dir)) {
            List<File> todaysBackups = stream
                    .filter(p -> p.toString().endsWith(EXT_XLSX) || p.toString().endsWith(".sql"))
                    .map(Path::toFile)
                    .filter(f -> {
                        LocalDateTime fd = parseDateFromFilename(f.getName());
                        return fd != null && fd.toLocalDate().equals(now.toLocalDate());
                    })
                    .toList();

            Map<LocalDateTime, List<File>> grouped = new HashMap<>();
            for (File f : todaysBackups) {
                LocalDateTime fd = parseDateFromFilename(f.getName());
                grouped.computeIfAbsent(fd, k -> new ArrayList<>()).add(f);
            }

            if (grouped.size() > 1) {
                List<LocalDateTime> sortedTimes = new ArrayList<>(grouped.keySet());
                Collections.sort(sortedTimes);
                for (int i = 0; i < sortedTimes.size() - 1; i++) {
                    for (File f : grouped.get(sortedTimes.get(i))) {
                        deleteMidDayBackup(f.toPath());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Mid-day cleanup failed", e);
        }
    }

    private void cleanupManualBackups() {
        Path dirPath = Paths.get(pathStrategy.getManualDir());
        if (!Files.exists(dirPath)) return;

        try (Stream<Path> stream = Files.list(dirPath)) {
            processManualBackupFiles(stream);
        } catch (IOException e) {
            log.warn("Manual cleanup failed", e);
        }
    }

    void processManualBackupFiles(Stream<Path> stream) {
        List<File> files = stream
                .filter(this::isBackupFile)
                .map(Path::toFile)
                .sorted(this::compareBackupFiles)
                .toList();

        deleteExcessManualBackups(files);
    }

    private boolean isBackupFile(Path p) {
        String pathStr = p.toString();
        return pathStr.endsWith(EXT_XLSX) || pathStr.endsWith(".sql");
    }

    private int compareBackupFiles(File f1, File f2) {
        LocalDateTime d1 = parseDateFromFilename(f1.getName());
        LocalDateTime d2 = parseDateFromFilename(f2.getName());
        if (d1 == null && d2 == null) return Long.compare(f1.lastModified(), f2.lastModified());
        if (d1 == null) return -1;
        if (d2 == null) return 1;
        return d1.compareTo(d2);
    }

    void deleteExcessManualBackups(List<File> files) {
        if (files.size() > 40) {
            int toDeleteCount = files.size() - 40;
            for (int i = 0; i < toDeleteCount; i++) {
                deleteSingleManualBackup(files.get(i));
            }
        }
    }

    private void deleteSingleManualBackup(File f) {
        try {
            Files.delete(f.toPath());
            auditoriaService.registrarAccion(0L, BACKUP_CLEANUP, "Removed old manual backup: " + f.getName());
        } catch (IOException e) {
            log.warn("Failed to delete manual backup: {}", f.getName());
        }
    }


    private void deleteMidDayBackup(Path p) {
        try {
            Files.delete(p);
            auditoriaService.registrarAccion(0L, BACKUP_CLEANUP,
                    "Removed Mid-Day Backup: " + p.getFileName());
        } catch (IOException e) {
            log.warn("Failed to delete mid-day backup: {}", p);
        }
    }
    // --- Helper Methods for Excel Export ---

    private void populateProductsSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle,
                                       CellStyle evenStyle, CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Productos");
        String[] headers = { "ID", "Código", "Descripción", "Costo", "Precio Mayorista", "Precio Minorista", "Stock" };
        createHeader(sheet, headers, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 20 * 256); // Codigo
        sheet.setColumnWidth(2, 60 * 256); // Descripcion (Wide for wrapping)
        sheet.setColumnWidth(3, 15 * 256); // Costo
        sheet.setColumnWidth(4, 15 * 256); // Mayorista
        sheet.setColumnWidth(5, 15 * 256); // Minorista
        sheet.setColumnWidth(6, 12 * 256); // Stock

        List<Product> products = productRepository.findAll();
        int rowNum = 1;
        for (Product p : products) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0; // rowNum was incremented, so odd rowNum variable means even data row
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, p.getId(), base);
            createTextCell(row, 1, p.getCodigo(), base);
            createTextCell(row, 2, p.getDescripcion(), base);
            createNumericCell(row, 3, p.getPrecioCosto(), curr);
            createNumericCell(row, 4, p.getPrecioMayorista(), curr);
            createNumericCell(row, 5, p.getPrecioMinorista(), curr);
            createTextCell(row, 6, p.getCantidadStock(), base);
        }
    }

    void populateSalesSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle,
                            CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Ventas");
        String[] headers = { "ID", FECHA, "Cliente", "Tipo Venta", "Desc. Global", "Recargo Global", "Total", "Usuario ID" };
        createHeader(sheet, headers, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 25 * 256); // Fecha
        sheet.setColumnWidth(2, 40 * 256); // Cliente
        sheet.setColumnWidth(3, 15 * 256); // Tipo Venta
        sheet.setColumnWidth(4, 15 * 256); // Desc. Global
        sheet.setColumnWidth(5, 15 * 256); // Recargo Global
        sheet.setColumnWidth(6, 15 * 256); // Total
        sheet.setColumnWidth(7, 12 * 256); // Usuario ID

        List<Venta> sales = ventaRepository.findAll();
        int rowNum = 1;
        for (Venta v : sales) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, v.getId(), base);
            createTextCell(row, 1, formatIsoDateForExcel(v.getFecha()), base);
            createTextCell(row, 2, v.getClienteNombre(), base);
            createTextCell(row, 3, v.getTipoVenta(), base);
            createNumericCell(row, 4, v.getDescuentoGlobal(), curr);
            createNumericCell(row, 5, v.getRecargoGlobal() != null ? v.getRecargoGlobal() : 0.0, curr);
            createNumericCell(row, 6, v.getTotalVenta(), curr);
            createTextCell(row, 7, v.getUsuarioId(), base);
        }
    }

    private void populatePurchasesSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle,
                                        CellStyle evenStyle, CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Compras");
        createHeader(sheet, new String[] { "ID", FECHA, "Proveedor", "Comprobante", "Total" }, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 25 * 256); // Fecha
        sheet.setColumnWidth(2, 40 * 256); // Proveedor
        sheet.setColumnWidth(3, 20 * 256); // Comprobante
        sheet.setColumnWidth(4, 15 * 256); // Total

        int rowNum = 1;
        var compras = compraRepository.findAll();
        for (var c : compras) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, c.getId(), base);
            createTextCell(row, 1, formatIsoDateForExcel(c.getFecha()), base);
            createTextCell(row, 2, c.getProveedor(), base);
            createTextCell(row, 3, c.getNroComprobante(), base);
            createNumericCell(row, 4, c.getTotalCompra(), curr);
        }
    }

    private void populateDebtorsSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle,
                                      CellStyle currOdd, CellStyle currEven) {
        Sheet sheet = workbook.createSheet("Deudores");
        createHeader(sheet, new String[] { "ID", "Venta ID", "Cliente", "Monto Deuda", FECHA, "Estado" }, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 15 * 256); // Venta ID
        sheet.setColumnWidth(2, 40 * 256); // Cliente
        sheet.setColumnWidth(3, 15 * 256); // Monto
        sheet.setColumnWidth(4, 25 * 256); // Fecha
        sheet.setColumnWidth(5, 15 * 256); // Estado

        int rowNum = 1;
        var deudores = deudoresRepository.findAll();
        for (var d : deudores) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;
            CellStyle curr = isEven ? currEven : currOdd;

            createTextCell(row, 0, d.getId(), base);
            createTextCell(row, 1, d.getVentaId(), base);
            createTextCell(row, 2, d.getClienteNombre(), base);
            createNumericCell(row, 3, d.getMontoDeuda(), curr);
            createTextCell(row, 4, formatIsoDateForExcel(d.getFechaDeuda()), base);
            createTextCell(row, 5, d.getEstado(), base);
        }
    }

    private void populateStockSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle) {
        Sheet sheet = workbook.createSheet("Stock_Ubicacion");
        createHeader(sheet, new String[] { "Producto ID", "Código", "Descripción", "Cantidad", "Ubicación" },
                headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 20 * 256); // Codigo
        sheet.setColumnWidth(2, 60 * 256); // Descripcion
        sheet.setColumnWidth(3, 15 * 256); // Cantidad
        sheet.setColumnWidth(4, 30 * 256); // Ubicacion

        String sqlStock = """
                    SELECT p.id, p.codigo, p.descripcion, s.cantidad, u.nombre
                    FROM stock_por_ubicacion s
                    JOIN productos p ON s.producto_id = p.id
                    JOIN ubicaciones u ON s.ubicacion_id = u.id
                """;
        int rowNum = 1;
        var stockRows = jdbcTemplate.queryForList(sqlStock);
        for (var map : stockRows) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;

            createTextCell(row, 0, map.get("id"), base);
            createTextCell(row, 1, map.get("codigo"), base);
            createTextCell(row, 2, map.get("descripcion"), base);
            createTextCell(row, 3, map.get("cantidad"), base);
            createTextCell(row, 4, map.get("nombre"), base);
        }
    }

    private void populateAuditSheet(Workbook workbook, CellStyle headerStyle, CellStyle oddStyle, CellStyle evenStyle) {
        Sheet sheet = workbook.createSheet("Auditoria");
        createHeader(sheet, new String[] { "ID", FECHA, "Acción", "Detalles", "Usuario ID" }, headerStyle);

        sheet.setColumnWidth(0, 15 * 256); // ID
        sheet.setColumnWidth(1, 30 * 256); // Fecha (Formatted YYYY-MM-DD -- HH:mm:ss)
        sheet.setColumnWidth(2, 25 * 256); // Accion
        sheet.setColumnWidth(3, 80 * 256); // Detalles (Wide for wrapping)
        sheet.setColumnWidth(4, 15 * 256); // Usuario ID

        int rowNum = 1;
        var audits = auditoriaRepository.findAll();
        for (var a : audits) {
            Row row = sheet.createRow(rowNum++);
            boolean isEven = rowNum % 2 != 0;
            CellStyle base = isEven ? evenStyle : oddStyle;

            createTextCell(row, 0, a.getId(), base);
            createTextCell(row, 1, formatIsoDateForExcel(a.getFechaHora()), base);
            createTextCell(row, 2, a.getAccion(), base);
            createTextCell(row, 3, a.getDetalles(), base);
            createTextCell(row, 4, a.getUsuarioId() != null ? a.getUsuarioId() : 0, base);
        }
    }
}
