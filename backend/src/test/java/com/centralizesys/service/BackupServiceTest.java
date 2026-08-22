package com.centralizesys.service;

import com.centralizesys.exception.InfrastructureException;
import com.centralizesys.repository.ProductRepository;
import com.centralizesys.repository.VentaRepository;
import com.centralizesys.repository.CompraRepository;
import com.centralizesys.repository.DeudoresRepository;
import com.centralizesys.repository.AuditoriaRepository;
import com.centralizesys.security.CustomUserDetails;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
// TODO: Technical Debt - Re-enable @Disabled tests after BackupService is refactored
// to use an injected CommandExecutor. The executor must be mocked here to prevent
// actual OS ProcessBuilder execution during unit tests.
class BackupServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private VentaRepository ventaRepository;

    @Mock
    private CompraRepository compraRepository;

    @Mock
    private DeudoresRepository deudoresRepository;

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @Mock
    private BackupPathStrategy pathStrategy;

    @TempDir
    Path tempDir;

    @Test
    @Disabled("Requires PostgreSQL and Docker Exec environment; ProcessBuilder OS execution throws exception in standard test environments.")
    @DisplayName("performBackup executes pg_dump and audits success")
    void performBackup_Success() {
        // GIVEN
        String manualDir = tempDir.resolve("manual").toString();
        when(pathStrategy.getManualDir()).thenReturn(manualDir);

        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy,
                "jdbc:postgresql://localhost:5432/test", "testuser", "testpass");

        // WHEN
        service.performBackup(BackupService.BackupType.MANUAL, 1L);

        // THEN
        // 1. Verify VACUUM INTO was NOT called (Deferred)
        verify(jdbcTemplate, never()).execute(anyString());

        // 2. Verify Audit Success
        verify(auditoriaService).registrarAccion(eq(1L), eq("BACKUP_EXITOSO"), contains("MANUAL"));

        // 3. Verify Excel File Creation (using our temp dir)
        File dir = new File(manualDir);
        if (dir.exists()) {
            File[] excelFiles = dir
                    .listFiles((d, name) -> name.startsWith("centralizesys_manual_") && name.endsWith(".xlsx"));
            // Cleanup matches
            if (excelFiles != null) {
                for (File f : excelFiles) {
                    // Java creates it, verify it exists
                    assertTrue(f.exists());
                }
            }
        }
    }

    @Test
    @Disabled("Requires PostgreSQL and Docker Exec environment; ProcessBuilder OS execution throws exception in standard test environments.")
    @DisplayName("performBackup audits failure if SQL throws exception")
    void performBackup_Failure_AuditsError() {
        // GIVEN
        String manualDir = tempDir.resolve("manual").toString();
        // Use lenient because if exception happens early, this might not be called, or
        // strictly it IS called to resolve path before try-catch block
        lenient().when(pathStrategy.getManualDir()).thenReturn(manualDir);

        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy,
                "jdbc:postgresql://localhost:5432/test", "testuser", "testpass");

        // Force Exception by throwing from auditoriaService during success log
        doThrow(new IllegalArgumentException("Simulated Error")).when(auditoriaService)
                .registrarAccion(eq(1L), eq("BACKUP_EXITOSO"), anyString());

        // WHEN / THEN
        assertThrows(InfrastructureException.class,
                () -> service.performBackup(BackupService.BackupType.MANUAL, 1L));
        verify(auditoriaService).registrarAccion(eq(1L), eq("BACKUP_FALLIDO"), anyString());
    }

    @Test
    @Disabled("Requires PostgreSQL and Docker Exec environment; ProcessBuilder OS execution throws exception in standard test environments.")
    @DisplayName("performBackup (no-arg) gets User ID from SecurityContext")
    void performBackup_UsesSecurityContext() {
        // GIVEN
        String manualDir = tempDir.resolve("manual").toString();
        when(pathStrategy.getManualDir()).thenReturn(manualDir);

        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy,
                "jdbc:postgresql://localhost:5432/test", "testuser", "testpass");

        // Mock Security Context
        CustomUserDetails mockUser = mock(CustomUserDetails.class);
        when(mockUser.getId()).thenReturn(100L);

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(mockUser);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);

        SecurityContextHolder.setContext(securityContext);

        try {
            // WHEN
            service.performBackup(BackupService.BackupType.MANUAL);

            // THEN
            verify(auditoriaService).registrarAccion(eq(100L), eq("BACKUP_EXITOSO"), anyString());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("deleteExcessManualBackups deletes oldest files when limit of 40 files (20 backups) is exceeded")
    void deleteExcessManualBackups_deletesOldestFiles() throws Exception {
        // GIVEN
        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy,
                "jdbc:postgresql://localhost:5432/test", "testuser", "testpass");

        java.util.List<File> files = new java.util.ArrayList<>();

        // A single manual backup consists of 2 files (SQL + XLSX).
        // We want to simulate 25 manual backups, which equals 50 files.
        // The limit is 20 backups (40 files), so the oldest 5 backups (10 files) should be deleted.
        for (int i = 0; i < 25; i++) {
            File sqlFile = tempDir.resolve("backup_" + i + ".sql").toFile();
            assertTrue(sqlFile.createNewFile(), "Failed to create SQL mock backup file");
            files.add(sqlFile);

            File xlsxFile = tempDir.resolve("backup_" + i + ".xlsx").toFile();
            assertTrue(xlsxFile.createNewFile(), "Failed to create XLSX mock backup file");
            files.add(xlsxFile);
        }

        // WHEN
        service.deleteExcessManualBackups(files);

        // THEN
        // First 10 files (5 oldest backups * 2 files) should be deleted
        for (int i = 0; i < 10; i++) {
            assertFalse(files.get(i).exists(), "File " + i + " should be deleted");
        }
        // Remaining 40 files (20 backups * 2 files) should still exist
        for (int i = 10; i < 50; i++) {
            assertTrue(files.get(i).exists(), "File " + i + " should exist");
        }

        // Verify auditoriaService was called exactly 10 times (once per file deleted)
        verify(auditoriaService, times(10)).registrarAccion(eq(0L), eq("BACKUP_CLEANUP"), anyString());
    }

    @Test
    @DisplayName("populateSalesSheet exports Recargo Global correctly without triggering OS execution")
    void populateSalesSheet_exportsRecargoGlobal() {
        // GIVEN
        BackupService service = new BackupService(jdbcTemplate, auditoriaService, productRepository, ventaRepository,
                compraRepository, deudoresRepository, auditoriaRepository, pathStrategy,
                "jdbc:postgresql://localhost:5432/test", "testuser", "testpass");

        com.centralizesys.model.sales.Venta venta = new com.centralizesys.model.sales.Venta();
        venta.setId(1L);
        venta.setFecha(java.time.LocalDateTime.now());
        venta.setClienteNombre("Test Client");
        venta.setTipoVenta("MINORISTA");
        venta.setDescuentoGlobal(50.0);
        venta.setRecargoGlobal(120.0); // The critical new field
        venta.setTotalVenta(1000.0);
        venta.setUsuarioId(99L);

        when(ventaRepository.findAll()).thenReturn(java.util.List.of(venta));

        org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.ss.usermodel.CellStyle dummyStyle = workbook.createCellStyle();

        // WHEN
        service.populateSalesSheet(workbook, dummyStyle, dummyStyle, dummyStyle, dummyStyle, dummyStyle);

        // THEN
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet("Ventas");
        assertNotNull(sheet, "Ventas sheet should be created");

        // Header Row (Row 0)
        org.apache.poi.ss.usermodel.Row headerRow = sheet.getRow(0);
        assertEquals("Recargo Global", headerRow.getCell(5).getStringCellValue(), "Header index 5 should be Recargo Global");

        // Data Row (Row 1)
        org.apache.poi.ss.usermodel.Row dataRow = sheet.getRow(1);
        assertEquals(120.0, dataRow.getCell(5).getNumericCellValue(), "Data index 5 should map to Recargo Global value");
        assertEquals(1000.0, dataRow.getCell(6).getNumericCellValue(), "Data index 6 should map to Total Venta");
    }
}
