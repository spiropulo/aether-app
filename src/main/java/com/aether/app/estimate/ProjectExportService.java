package com.aether.app.estimate;

import com.aether.app.offer.Offer;
import com.aether.app.offer.OfferRepository;
import com.aether.app.project.Project;
import com.aether.app.project.ProjectService;
import com.aether.app.task.Task;
import com.aether.app.task.TaskRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates professionally formatted PDF exports of projects.
 * Available when project is "Fully Priced" (all offers have unitCost set) or on manual request.
 */
@Service
public class ProjectExportService {

    private static final Logger log = LoggerFactory.getLogger(ProjectExportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final ProjectService projectService;
    private final TaskRepository taskRepository;
    private final OfferRepository offerRepository;

    public ProjectExportService(ProjectService projectService,
                               TaskRepository taskRepository,
                               OfferRepository offerRepository) {
        this.projectService = projectService;
        this.taskRepository = taskRepository;
        this.offerRepository = offerRepository;
    }

    /**
     * A project is "Fully Priced" when every offer has unitCost != null (i.e. no unpriced line items).
     */
    public Mono<Boolean> isFullyPriced(String projectId, String tenantId) {
        return offerRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                .collectList()
                .map(offers -> offers.stream().allMatch(o -> o.getUnitCost() != null));
    }

    /**
     * Generate PDF bytes for the project. Returns empty Mono if project not found.
     */
    public Mono<byte[]> generatePdf(String projectId, String tenantId) {
        return projectService.getProject(projectId, tenantId)
                .flatMap(project -> taskRepository.findAllByProjectIdAndTenantId(projectId, tenantId)
                        .collectList()
                        .zipWith(offerRepository.findAllByProjectIdAndTenantId(projectId, tenantId).collectList())
                        .map(tuple -> {
                            List<Task> tasks = tuple.getT1();
                            List<Offer> offers = tuple.getT2();
                            Map<String, List<Offer>> offersByTask = offers.stream()
                                    .collect(Collectors.groupingBy(Offer::getTaskId));
                            return buildPdf(project, tasks, offersByTask);
                        }));
    }

    private byte[] buildPdf(Project project, List<Task> tasks, Map<String, List<Offer>> offersByTask) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.LETTER, 36, 36, 36, 36);
            PdfWriter.getInstance(doc, baos);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

            // Title
            Paragraph title = new Paragraph(project.getName(), titleFont);
            title.setSpacingAfter(8);
            doc.add(title);

            // Meta
            if (project.getDescription() != null && !project.getDescription().isBlank()) {
                doc.add(new Paragraph(project.getDescription(), normalFont));
                doc.add(Chunk.NEWLINE);
            }
            StringBuilder meta = new StringBuilder();
            if (project.getStatus() != null && !project.getStatus().isBlank()) {
                meta.append("Status: ").append(project.getStatus()).append("  ");
            }
            if (project.getStartDate() != null) {
                meta.append("Start: ").append(formatDate(project.getStartDate())).append("  ");
            }
            if (project.getEndDate() != null) {
                meta.append("End: ").append(formatDate(project.getEndDate()));
            }
            if (meta.length() > 0) {
                doc.add(new Paragraph(meta.toString(), smallFont));
                doc.add(Chunk.NEWLINE);
            }

            double projectTotal = 0;

            for (Task task : tasks) {
                doc.add(new Paragraph(task.getName(), headingFont));
                if (task.getDescription() != null && !task.getDescription().isBlank()) {
                    doc.add(new Paragraph(task.getDescription(), smallFont));
                }

                List<Offer> taskOffers = offersByTask.getOrDefault(task.getId(), List.of());
                if (taskOffers.isEmpty()) {
                    doc.add(new Paragraph("No line items.", smallFont));
                } else {
                    PdfPTable table = new PdfPTable(5);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[]{3, 2, 1, 1.5f, 1.5f});
                    table.setSpacingBefore(4);
                    table.setSpacingAfter(8);

                    table.addCell(headerCell("Item", smallFont));
                    table.addCell(headerCell("UoM", smallFont));
                    table.addCell(headerCell("Qty", smallFont));
                    table.addCell(headerCell("Unit Cost", smallFont));
                    table.addCell(headerCell("Total", smallFont));

                    double taskTotal = 0;
                    for (Offer o : taskOffers) {
                        table.addCell(cell(o.getName(), smallFont));
                        table.addCell(cell(o.getUom() != null ? o.getUom() : "—", smallFont));
                        table.addCell(cell(o.getQuantity() != null ? String.format("%.2f", o.getQuantity()) : "—", smallFont));
                        table.addCell(cell(o.getUnitCost() != null ? String.format("$%.2f", o.getUnitCost()) : "—", smallFont));
                        double total = o.getTotal() != null ? o.getTotal() : 0;
                        if (o.getQuantity() != null && o.getUnitCost() != null) {
                            total = o.getQuantity() * o.getUnitCost();
                        }
                        table.addCell(cell(String.format("$%.2f", total), smallFont));
                        taskTotal += total;
                    }
                    doc.add(table);
                    doc.add(new Paragraph("Task total: $" + String.format("%.2f", taskTotal), smallFont));
                    doc.add(Chunk.NEWLINE);
                    projectTotal += taskTotal;
                }
            }

            doc.add(new Paragraph("—".repeat(40), normalFont));
            doc.add(new Paragraph("Project total: $" + String.format("%.2f", projectTotal), headingFont));

            doc.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate project PDF", e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage());
        }
    }

    private static PdfPCell headerCell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setBackgroundColor(new java.awt.Color(0xf3, 0xf4, 0xf6));
        c.setPadding(4);
        return c;
    }

    private static PdfPCell cell(String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(4);
        return c;
    }

    private static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isBlank()) return "—";
        try {
            LocalDate d = LocalDate.parse(isoDate.substring(0, Math.min(10, isoDate.length())));
            return d.format(DATE_FMT);
        } catch (Exception e) {
            return isoDate;
        }
    }
}
