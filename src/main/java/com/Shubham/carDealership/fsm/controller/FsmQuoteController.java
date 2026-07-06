package com.Shubham.carDealership.fsm.controller;

import com.Shubham.carDealership.config.JwtUtil;
import com.Shubham.carDealership.fsm.model.FsmCustomer;
import com.Shubham.carDealership.fsm.model.FsmJob;
import com.Shubham.carDealership.fsm.model.FsmQuote;
import com.Shubham.carDealership.fsm.repository.FsmCustomerRepository;
import com.Shubham.carDealership.fsm.repository.FsmJobRepository;
import com.Shubham.carDealership.fsm.repository.FsmQuoteRepository;
import com.Shubham.carDealership.model.User;
import com.Shubham.carDealership.repository.UserRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fsm/quotes")
public class FsmQuoteController {

    @Autowired private FsmQuoteRepository     repo;
    @Autowired private FsmCustomerRepository  customerRepo;
    @Autowired private FsmJobRepository       jobRepo;
    @Autowired private UserRepository         userRepo;
    @Autowired private JwtUtil                jwtUtil;
    @Autowired private com.Shubham.carDealership.service.EmailService emailService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final Color ACCENT  = new Color(255, 122, 26);
    private static final Color DARK    = new Color(15, 17, 23);
    private static final Color MUTED   = new Color(100, 116, 139);
    private static final Color DIVIDER = new Color(30, 41, 59);

    private Long ownerId(HttpServletRequest req) {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            String token = h.substring(7);
            if (jwtUtil.validateToken(token)) return jwtUtil.extractUserId(token);
        }
        return null;
    }

    private Map<String, Object> toDto(FsmQuote q) {
        BigDecimal subtotal = q.getSubtotal() != null ? q.getSubtotal() : BigDecimal.ZERO;
        BigDecimal gstAmt   = q.isGstEnabled()
                ? subtotal.multiply(q.getGstRate()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal total    = subtotal.add(gstAmt);

        Map<String, Object> cust = new HashMap<>();
        if (q.getCustomer() != null) {
            FsmCustomer c = q.getCustomer();
            cust.put("id",    c.getId());
            cust.put("name",  c.getName()  != null ? c.getName()  : "");
            cust.put("email", c.getEmail() != null ? c.getEmail() : "");
            cust.put("phone", c.getPhone() != null ? c.getPhone() : "");
        }

        Map<String, Object> dto = new HashMap<>();
        dto.put("id",             q.getId());
        dto.put("status",         q.getStatus());
        dto.put("title",          q.getTitle()       != null ? q.getTitle()       : "");
        dto.put("description",    q.getDescription() != null ? q.getDescription() : "");
        dto.put("notes",          q.getNotes()       != null ? q.getNotes()       : "");
        dto.put("subtotal",       subtotal);
        dto.put("gstEnabled",     q.isGstEnabled());
        dto.put("gstRate",        q.getGstRate());
        dto.put("gstAmount",      gstAmt);
        dto.put("total",          total);
        dto.put("customer",       cust);
        dto.put("validUntil",     q.getValidUntil()  != null ? q.getValidUntil().toString()  : "");
        dto.put("createdAt",      q.getCreatedAt()   != null ? q.getCreatedAt().toString()   : "");
        dto.put("convertedJobId", q.getConvertedJobId() != null ? q.getConvertedJobId() : 0);
        return dto;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String status, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        List<FsmQuote> quotes = (status != null && !status.isBlank())
                ? repo.findByBusinessOwnerIdAndStatusOrderByCreatedAtDesc(owner, status.toUpperCase())
                : repo.findByBusinessOwnerIdOrderByCreatedAtDesc(owner);
        return ResponseEntity.ok(quotes.stream().map(this::toDto).collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(q -> q.getBusinessOwnerId().equals(owner))
                .map(q -> ResponseEntity.ok(toDto(q)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));

        FsmQuote q = new FsmQuote();
        q.setBusinessOwnerId(owner);
        if (body.get("title")       != null) q.setTitle(body.get("title").toString());
        if (body.get("description") != null) q.setDescription(body.get("description").toString());
        if (body.get("notes")       != null) q.setNotes(body.get("notes").toString());
        if (body.get("subtotal")    != null) q.setSubtotal(new BigDecimal(body.get("subtotal").toString()));
        if (body.get("gstEnabled")  != null) q.setGstEnabled(Boolean.parseBoolean(body.get("gstEnabled").toString()));
        if (body.get("validUntil")  != null && !body.get("validUntil").toString().isBlank())
            q.setValidUntil(LocalDateTime.parse(body.get("validUntil").toString()));
        if (body.get("customerId")  != null) {
            Long cid = Long.valueOf(body.get("customerId").toString());
            customerRepo.findById(cid)
                    .filter(c -> c.getBusinessOwnerId().equals(owner))
                    .ifPresent(q::setCustomer);
        }
        return ResponseEntity.ok(toDto(repo.save(q)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(q -> q.getBusinessOwnerId().equals(owner))
                .map(q -> {
                    if (body.get("title")       != null) q.setTitle(body.get("title").toString());
                    if (body.get("description") != null) q.setDescription(body.get("description").toString());
                    if (body.get("notes")       != null) q.setNotes(body.get("notes").toString());
                    if (body.get("subtotal")    != null) q.setSubtotal(new BigDecimal(body.get("subtotal").toString()));
                    if (body.get("gstEnabled")  != null) q.setGstEnabled(Boolean.parseBoolean(body.get("gstEnabled").toString()));
                    if (body.get("status")      != null) q.setStatus(body.get("status").toString());
                    if (body.get("validUntil")  != null && !body.get("validUntil").toString().isBlank())
                        q.setValidUntil(LocalDateTime.parse(body.get("validUntil").toString()));
                    if (body.get("customerId")  != null) {
                        Long cid = Long.valueOf(body.get("customerId").toString());
                        customerRepo.findById(cid)
                                .filter(c -> c.getBusinessOwnerId().equals(owner))
                                .ifPresent(q::setCustomer);
                    }
                    return ResponseEntity.ok(toDto(repo.save(q)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(q -> q.getBusinessOwnerId().equals(owner))
                .map(q -> {
                    String newStatus = body.get("status");
                    q.setStatus(newStatus);
                    if ("SENT".equals(newStatus) && q.getSentAt() == null) {
                        q.setSentAt(LocalDateTime.now());
                        FsmQuote saved = repo.save(q);
                        // Email the quote to the customer
                        if (q.getCustomer() != null) {
                            User owner2 = userRepo.findById(owner).orElse(null);
                            String bizName = owner2 != null && owner2.getBusinessName() != null ? owner2.getBusinessName() : "FieldFlow";
                            BigDecimal subtotal = q.getSubtotal() != null ? q.getSubtotal() : BigDecimal.ZERO;
                            BigDecimal gstAmt = q.isGstEnabled() ? subtotal.multiply(q.getGstRate()).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
                            String total = "$" + subtotal.add(gstAmt).setScale(2, RoundingMode.HALF_UP);
                            String validUntil = q.getValidUntil() != null ? q.getValidUntil().format(DATE_FMT) : null;
                            emailService.sendQuoteToCustomer(
                                q.getCustomer().getEmail(),
                                q.getCustomer().getName(),
                                bizName, q.getTitle(),
                                q.getDescription(), total, validUntil
                            );
                        }
                        return ResponseEntity.ok(toDto(saved));
                    }
                    if ("ACCEPTED".equals(newStatus) && q.getAcceptedAt() == null) q.setAcceptedAt(LocalDateTime.now());
                    return ResponseEntity.ok(toDto(repo.save(q)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Convert accepted quote to a job
    @PostMapping("/{id}/convert")
    public ResponseEntity<?> convertToJob(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(q -> q.getBusinessOwnerId().equals(owner))
                .map(q -> {
                    FsmJob job = new FsmJob();
                    job.setBusinessOwnerId(owner);
                    job.setJobType(q.getTitle() != null ? q.getTitle() : "Service");
                    job.setDescription(q.getDescription());
                    job.setNotes(q.getNotes());
                    BigDecimal subtotal = q.getSubtotal() != null ? q.getSubtotal() : BigDecimal.ZERO;
                    BigDecimal gstAmt   = q.isGstEnabled()
                            ? subtotal.multiply(q.getGstRate()).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    job.setAmount(subtotal.add(gstAmt));
                    job.setStatus("NEW");
                    job.setPriority("NORMAL");
                    if (q.getCustomer() != null) job.setCustomer(q.getCustomer());
                    FsmJob saved = jobRepo.save(job);
                    q.setConvertedJobId(saved.getId());
                    q.setStatus("ACCEPTED");
                    q.setAcceptedAt(LocalDateTime.now());
                    repo.save(q);
                    return ResponseEntity.ok(Map.of("jobId", saved.getId(), "message", "Quote converted to job #" + saved.getId()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        return repo.findById(id)
                .filter(q -> q.getBusinessOwnerId().equals(owner))
                .map(q -> { repo.delete(q); return ResponseEntity.ok(Map.of("success", true)); })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id, HttpServletRequest req) {
        Long owner = ownerId(req);
        if (owner == null) return ResponseEntity.status(401).build();
        return repo.findById(id)
                .filter(q -> q.getBusinessOwnerId().equals(owner))
                .map(q -> {
                    User user = userRepo.findById(owner).orElse(null);
                    byte[] pdf = generatePdf(q, user);
                    String filename = "quote-" + String.format("%04d", q.getId()) + ".pdf";
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                            .contentType(MediaType.APPLICATION_PDF)
                            .body(pdf);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private byte[] generatePdf(FsmQuote q, User user) {
        String bizName    = user != null && user.getBusinessName()    != null ? user.getBusinessName()    : "FieldFlow";
        String bizAddress = user != null && user.getBusinessAddress() != null ? user.getBusinessAddress() : null;
        String bizGst     = user != null && user.getBusinessAbn()     != null ? user.getBusinessAbn()     : null;
        String bizPhone   = user != null && user.getPhoneNumber()     != null ? user.getPhoneNumber()     : null;
        String bizEmail   = user != null && user.getEmail()           != null ? user.getEmail()           : null;

        BigDecimal subtotal = q.getSubtotal() != null ? q.getSubtotal() : BigDecimal.ZERO;
        BigDecimal gstAmt   = q.isGstEnabled()
                ? subtotal.multiply(q.getGstRate()).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal total    = subtotal.add(gstAmt);

        String custName  = q.getCustomer() != null ? q.getCustomer().getName()  : "—";
        String custPhone = q.getCustomer() != null ? q.getCustomer().getPhone() : "";
        String custEmail = q.getCustomer() != null ? q.getCustomer().getEmail() : "";

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            Font fontBrand  = new Font(Font.HELVETICA, 22, Font.BOLD, ACCENT);
            Font fontH1     = new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE);
            Font fontLabel  = new Font(Font.HELVETICA, 9,  Font.BOLD, MUTED);
            Font fontValue  = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.WHITE);
            Font fontMuted  = new Font(Font.HELVETICA, 9,  Font.NORMAL, MUTED);
            Font fontBig    = new Font(Font.HELVETICA, 28, Font.BOLD, ACCENT);

            PdfContentByte cb = writer.getDirectContent();
            cb.setColorFill(DARK);
            cb.rectangle(0, PageSize.A4.getHeight() - 130, PageSize.A4.getWidth(), 130);
            cb.fill();

            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{1, 1});

            PdfPCell brandCell = new PdfPCell();
            brandCell.setBorder(Rectangle.NO_BORDER);
            brandCell.setBackgroundColor(DARK);
            brandCell.setPadding(16);
            brandCell.addElement(new Paragraph(bizName, fontBrand));
            if (bizAddress != null) brandCell.addElement(new Paragraph(bizAddress, fontMuted));
            if (bizPhone   != null) brandCell.addElement(new Paragraph(bizPhone, fontMuted));
            if (bizEmail   != null) brandCell.addElement(new Paragraph(bizEmail, fontMuted));
            if (bizGst     != null) brandCell.addElement(new Paragraph("GST No: " + bizGst, fontMuted));
            header.addCell(brandCell);

            PdfPCell qCell = new PdfPCell();
            qCell.setBorder(Rectangle.NO_BORDER);
            qCell.setBackgroundColor(DARK);
            qCell.setPadding(16);
            qCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph qNum = new Paragraph("QUO-" + String.format("%04d", q.getId()), fontH1);
            qNum.setAlignment(Element.ALIGN_RIGHT);
            qCell.addElement(qNum);
            Paragraph qStat = new Paragraph(q.getStatus(), new Font(Font.HELVETICA, 10, Font.BOLD,
                    "ACCEPTED".equals(q.getStatus()) ? new Color(52, 211, 153) :
                    "DECLINED".equals(q.getStatus()) ? new Color(255, 92, 108) :
                    "SENT".equals(q.getStatus())     ? new Color(96, 165, 250) :
                    new Color(252, 211, 77)));
            qStat.setAlignment(Element.ALIGN_RIGHT);
            qCell.addElement(qStat);
            header.addCell(qCell);
            doc.add(header);

            doc.add(Chunk.NEWLINE);

            Paragraph amtLabel = new Paragraph("QUOTE TOTAL", fontLabel);
            amtLabel.setAlignment(Element.ALIGN_CENTER);
            doc.add(amtLabel);
            Paragraph amtValue = new Paragraph("$" + String.format("%.2f", total), fontBig);
            amtValue.setAlignment(Element.ALIGN_CENTER);
            doc.add(amtValue);

            doc.add(Chunk.NEWLINE);
            addDivider(doc);
            doc.add(Chunk.NEWLINE);

            PdfPTable cols = new PdfPTable(2);
            cols.setWidthPercentage(100);
            cols.setWidths(new float[]{1, 1});

            PdfPCell billCell = new PdfPCell();
            billCell.setBorder(Rectangle.NO_BORDER);
            billCell.setPaddingRight(20);
            billCell.addElement(new Paragraph("PREPARED FOR", fontLabel));
            billCell.addElement(new Paragraph(custName, fontValue));
            if (custPhone != null && !custPhone.isBlank()) billCell.addElement(new Paragraph(custPhone, fontMuted));
            if (custEmail != null && !custEmail.isBlank()) billCell.addElement(new Paragraph(custEmail, fontMuted));
            cols.addCell(billCell);

            PdfPCell detCell = new PdfPCell();
            detCell.setBorder(Rectangle.NO_BORDER);
            detCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addKV(detCell, "QUOTE NUMBER", "QUO-" + String.format("%04d", q.getId()), fontLabel, fontValue);
            addKV(detCell, "DATE", q.getCreatedAt() != null ? q.getCreatedAt().format(DATE_FMT) : "—", fontLabel, fontValue);
            if (q.getValidUntil() != null)
                addKV(detCell, "VALID UNTIL", q.getValidUntil().format(DATE_FMT), fontLabel, fontValue);
            cols.addCell(detCell);
            doc.add(cols);

            doc.add(Chunk.NEWLINE);
            addDivider(doc);
            doc.add(Chunk.NEWLINE);

            if (q.getTitle() != null && !q.getTitle().isBlank()) {
                Paragraph titleP = new Paragraph(q.getTitle(), new Font(Font.HELVETICA, 13, Font.BOLD, Color.WHITE));
                doc.add(titleP);
                doc.add(Chunk.NEWLINE);
            }
            if (q.getDescription() != null && !q.getDescription().isBlank()) {
                doc.add(new Paragraph(q.getDescription(), fontValue));
                doc.add(Chunk.NEWLINE);
            }

            PdfPTable items = new PdfPTable(2);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{5, 1.4f});
            addTableHeader(items, "DESCRIPTION", fontLabel);
            addTableHeader(items, "AMOUNT", fontLabel);

            PdfPCell descCell = new PdfPCell(new Phrase(q.getTitle() != null ? q.getTitle() : "Service", fontValue));
            descCell.setBorder(Rectangle.NO_BORDER); descCell.setPaddingTop(8); descCell.setPaddingBottom(8);
            items.addCell(descCell);
            PdfPCell priceCell = new PdfPCell(new Phrase("$" + String.format("%.2f", subtotal), fontValue));
            priceCell.setBorder(Rectangle.NO_BORDER); priceCell.setPaddingTop(8); priceCell.setPaddingBottom(8);
            priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            items.addCell(priceCell);
            doc.add(items);

            doc.add(Chunk.NEWLINE);
            addDivider(doc);
            doc.add(Chunk.NEWLINE);

            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(100);
            if (q.isGstEnabled()) {
                addTotalRow(totalTable, "SUBTOTAL (excl. GST)", "$" + String.format("%.2f", subtotal), fontLabel, fontValue);
                addTotalRow(totalTable, "GST (15%)",            "$" + String.format("%.2f", gstAmt),   fontLabel, fontValue);
            }
            PdfPCell tlLbl = new PdfPCell(new Phrase(q.isGstEnabled() ? "TOTAL (incl. GST)" : "TOTAL", fontLabel));
            tlLbl.setBorder(Rectangle.NO_BORDER); tlLbl.setPaddingTop(q.isGstEnabled() ? 4 : 0);
            totalTable.addCell(tlLbl);
            PdfPCell tlVal = new PdfPCell(new Phrase("$" + String.format("%.2f", total),
                    new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT)));
            tlVal.setBorder(Rectangle.NO_BORDER); tlVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            tlVal.setPaddingTop(q.isGstEnabled() ? 4 : 0);
            totalTable.addCell(tlVal);
            doc.add(totalTable);

            if (q.getNotes() != null && !q.getNotes().isBlank()) {
                doc.add(Chunk.NEWLINE);
                addDivider(doc);
                doc.add(Chunk.NEWLINE);
                doc.add(new Paragraph("NOTES", fontLabel));
                doc.add(new Paragraph(q.getNotes(), fontMuted));
            }

            doc.add(Chunk.NEWLINE);
            Paragraph footer = new Paragraph("Thank you for considering our services.", fontMuted);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);
            Paragraph footerBrand = new Paragraph("Generated by FieldFlow", fontMuted);
            footerBrand.setAlignment(Element.ALIGN_CENTER);
            doc.add(footerBrand);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate quote PDF", e);
        }
    }

    private void addDivider(Document doc) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorderWidthTop(1); cell.setBorderColorTop(DIVIDER);
        cell.setBorderWidthBottom(0); cell.setBorderWidthLeft(0); cell.setBorderWidthRight(0);
        cell.setFixedHeight(1);
        line.addCell(cell);
        doc.add(line);
    }

    private void addKV(PdfPCell parent, String label, String value, Font lf, Font vf) {
        Paragraph lp = new Paragraph(label, lf); lp.setAlignment(Element.ALIGN_RIGHT); parent.addElement(lp);
        Paragraph vp = new Paragraph(value, vf); vp.setAlignment(Element.ALIGN_RIGHT); parent.addElement(vp);
        parent.addElement(new Paragraph(" "));
    }

    private void addTableHeader(PdfPTable t, String text, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setBorderWidthBottom(1); c.setBorderColorBottom(DIVIDER);
        c.setBorderWidthTop(0); c.setBorderWidthLeft(0); c.setBorderWidthRight(0);
        c.setPaddingBottom(6);
        t.addCell(c);
    }

    private void addTotalRow(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell lc = new PdfPCell(new Phrase(label, lf));
        lc.setBorder(Rectangle.NO_BORDER); lc.setPaddingBottom(4); t.addCell(lc);
        PdfPCell vc = new PdfPCell(new Phrase(value, vf));
        vc.setBorder(Rectangle.NO_BORDER); vc.setHorizontalAlignment(Element.ALIGN_RIGHT); vc.setPaddingBottom(4);
        t.addCell(vc);
    }
}
