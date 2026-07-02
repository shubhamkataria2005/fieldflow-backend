package com.Shubham.carDealership.fsm.service;

import com.Shubham.carDealership.fsm.model.FsmInvoice;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Service
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MMMM d, yyyy");
    private static final Color ACCENT   = new Color(255, 122, 26);
    private static final Color DARK     = new Color(15,  17,  23);
    private static final Color MUTED    = new Color(100, 116, 139);
    private static final Color DIVIDER  = new Color(30,  41,  59);

    public byte[] generate(FsmInvoice inv, String businessName) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            String invNumber = "INV-" + String.format("%04d", inv.getId());
            String status    = inv.getStatus() != null ? inv.getStatus() : "UNPAID";
            String jobType   = inv.getJob() != null ? inv.getJob().getJobType() : "Service";
            String address   = inv.getJob() != null && inv.getJob().getAddress() != null ? inv.getJob().getAddress() : "";
            Long   jobId     = inv.getJob() != null ? inv.getJob().getId() : null;
            String custName  = inv.getCustomer() != null ? inv.getCustomer().getName()  : "—";
            String custPhone = inv.getCustomer() != null ? inv.getCustomer().getPhone() : "";
            String custEmail = inv.getCustomer() != null ? inv.getCustomer().getEmail() : "";
            String amount    = "$" + String.format("%.2f", inv.getAmount());

            Font fontBrand  = new Font(Font.HELVETICA, 22, Font.BOLD, ACCENT);
            Font fontH1     = new Font(Font.HELVETICA, 16, Font.BOLD, Color.WHITE);
            Font fontLabel  = new Font(Font.HELVETICA, 9,  Font.BOLD, MUTED);
            Font fontValue  = new Font(Font.HELVETICA, 10, Font.NORMAL, Color.WHITE);
            Font fontMuted  = new Font(Font.HELVETICA, 9,  Font.NORMAL, MUTED);
            Font fontBig    = new Font(Font.HELVETICA, 28, Font.BOLD, ACCENT);
            Font fontStatus = new Font(Font.HELVETICA, 10, Font.BOLD,
                "PAID".equals(status) ? new Color(52, 211, 153) :
                "OVERDUE".equals(status) ? new Color(255, 92, 108) :
                new Color(252, 211, 77));

            // ── Header bar ──
            PdfContentByte cb = writer.getDirectContent();
            cb.setColorFill(DARK);
            cb.rectangle(0, PageSize.A4.getHeight() - 110, PageSize.A4.getWidth(), 110);
            cb.fill();

            // Brand + invoice number row
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[]{1, 1});
            header.setSpacingAfter(0);

            PdfPCell brandCell = new PdfPCell();
            brandCell.setBorder(Rectangle.NO_BORDER);
            brandCell.setBackgroundColor(DARK);
            brandCell.setPadding(16);
            Paragraph brand = new Paragraph(businessName != null ? businessName : "FieldFlow", fontBrand);
            brandCell.addElement(brand);
            Paragraph brandSub = new Paragraph("Professional Services Invoice", fontMuted);
            brandCell.addElement(brandSub);
            header.addCell(brandCell);

            PdfPCell invCell = new PdfPCell();
            invCell.setBorder(Rectangle.NO_BORDER);
            invCell.setBackgroundColor(DARK);
            invCell.setPadding(16);
            invCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            Paragraph invNum = new Paragraph(invNumber, fontH1);
            invNum.setAlignment(Element.ALIGN_RIGHT);
            invCell.addElement(invNum);
            Paragraph invStatus = new Paragraph(status, fontStatus);
            invStatus.setAlignment(Element.ALIGN_RIGHT);
            invCell.addElement(invStatus);
            header.addCell(invCell);
            doc.add(header);

            doc.add(Chunk.NEWLINE);

            // ── Amount hero ──
            Paragraph amtLabel = new Paragraph("AMOUNT DUE", fontLabel);
            amtLabel.setAlignment(Element.ALIGN_CENTER);
            doc.add(amtLabel);
            Paragraph amtValue = new Paragraph(amount, fontBig);
            amtValue.setAlignment(Element.ALIGN_CENTER);
            doc.add(amtValue);

            if ("PAID".equals(status) && inv.getPaidAt() != null) {
                String paidLine = "Paid on " + inv.getPaidAt().format(DATE_FMT)
                    + (inv.getPaymentMethod() != null ? " via " + inv.getPaymentMethod() : "");
                Paragraph paidP = new Paragraph(paidLine, new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(52, 211, 153)));
                paidP.setAlignment(Element.ALIGN_CENTER);
                doc.add(paidP);
            }

            doc.add(Chunk.NEWLINE);
            addDivider(doc, cb, writer);
            doc.add(Chunk.NEWLINE);

            // ── Two-column: Bill To + Invoice Details ──
            PdfPTable cols = new PdfPTable(2);
            cols.setWidthPercentage(100);
            cols.setWidths(new float[]{1, 1});

            // Bill To
            PdfPCell billCell = new PdfPCell();
            billCell.setBorder(Rectangle.NO_BORDER);
            billCell.setPaddingRight(20);
            billCell.addElement(new Paragraph("BILL TO", fontLabel));
            billCell.addElement(new Paragraph(custName, fontValue));
            if (custPhone != null && !custPhone.isBlank())
                billCell.addElement(new Paragraph(custPhone, fontMuted));
            if (custEmail != null && !custEmail.isBlank())
                billCell.addElement(new Paragraph(custEmail, fontMuted));
            cols.addCell(billCell);

            // Invoice details
            PdfPCell detCell = new PdfPCell();
            detCell.setBorder(Rectangle.NO_BORDER);
            detCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            addKV(detCell, "INVOICE NUMBER", invNumber, fontLabel, fontValue);
            addKV(detCell, "ISSUED",
                inv.getIssuedAt() != null ? inv.getIssuedAt().format(DATE_FMT) : "—",
                fontLabel, fontValue);
            if (jobId != null)
                addKV(detCell, "JOB", "#" + jobId, fontLabel, fontValue);
            cols.addCell(detCell);

            doc.add(cols);
            doc.add(Chunk.NEWLINE);
            addDivider(doc, cb, writer);
            doc.add(Chunk.NEWLINE);

            // ── Line items table ──
            PdfPTable items = new PdfPTable(3);
            items.setWidthPercentage(100);
            items.setWidths(new float[]{5, 1, 1.4f});

            // Table header
            addTableHeader(items, "DESCRIPTION", fontLabel);
            addTableHeader(items, "QTY",         fontLabel);
            addTableHeader(items, "AMOUNT",       fontLabel);

            // Line item
            PdfPCell descCell = new PdfPCell(new Phrase(jobType + " Service", fontValue));
            descCell.setBorder(Rectangle.NO_BORDER);
            descCell.setPaddingTop(8);
            descCell.setPaddingBottom(8);
            items.addCell(descCell);

            PdfPCell qtyCell = new PdfPCell(new Phrase("1", fontValue));
            qtyCell.setBorder(Rectangle.NO_BORDER);
            qtyCell.setPaddingTop(8);
            qtyCell.setPaddingBottom(8);
            items.addCell(qtyCell);

            PdfPCell priceCell = new PdfPCell(new Phrase(amount, fontValue));
            priceCell.setBorder(Rectangle.NO_BORDER);
            priceCell.setPaddingTop(8);
            priceCell.setPaddingBottom(8);
            priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            items.addCell(priceCell);

            if (address != null && !address.isBlank()) {
                PdfPCell addrCell = new PdfPCell(new Phrase(address, fontMuted));
                addrCell.setBorder(Rectangle.NO_BORDER);
                addrCell.setColspan(3);
                addrCell.setPaddingBottom(8);
                items.addCell(addrCell);
            }

            doc.add(items);

            doc.add(Chunk.NEWLINE);
            addDivider(doc, cb, writer);
            doc.add(Chunk.NEWLINE);

            // ── Total row ──
            PdfPTable total = new PdfPTable(2);
            total.setWidthPercentage(100);
            PdfPCell tLbl = new PdfPCell(new Phrase("TOTAL", fontLabel));
            tLbl.setBorder(Rectangle.NO_BORDER);
            total.addCell(tLbl);
            PdfPCell tVal = new PdfPCell(new Phrase(amount, new Font(Font.HELVETICA, 14, Font.BOLD, ACCENT)));
            tVal.setBorder(Rectangle.NO_BORDER);
            tVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            total.addCell(tVal);
            doc.add(total);

            doc.add(Chunk.NEWLINE);
            doc.add(Chunk.NEWLINE);

            // ── Footer ──
            Paragraph footer = new Paragraph("Thank you for your business.", fontMuted);
            footer.setAlignment(Element.ALIGN_CENTER);
            doc.add(footer);
            Paragraph footerBrand = new Paragraph("Generated by FieldFlow", fontMuted);
            footerBrand.setAlignment(Element.ALIGN_CENTER);
            doc.add(footerBrand);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    private void addDivider(Document doc, PdfContentByte cb, PdfWriter writer) throws DocumentException {
        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBorderWidthTop(1);
        cell.setBorderColorTop(DIVIDER);
        cell.setBorderWidthBottom(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        cell.setFixedHeight(1);
        line.addCell(cell);
        doc.add(line);
    }

    private void addKV(PdfPCell parent, String label, String value, Font labelFont, Font valueFont) {
        Paragraph lbl = new Paragraph(label, labelFont);
        lbl.setAlignment(Element.ALIGN_RIGHT);
        parent.addElement(lbl);
        Paragraph val = new Paragraph(value, valueFont);
        val.setAlignment(Element.ALIGN_RIGHT);
        parent.addElement(val);
        parent.addElement(new Paragraph(" "));
    }

    private void addTableHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderWidthBottom(1);
        cell.setBorderColorBottom(DIVIDER);
        cell.setBorderWidthTop(0);
        cell.setBorderWidthLeft(0);
        cell.setBorderWidthRight(0);
        cell.setPaddingBottom(6);
        table.addCell(cell);
    }
}
