package cn.fj.roadagent.adapters.maintenance.docx;

import cn.fj.roadagent.application.maintenance.MaintenanceChart;
import cn.fj.roadagent.application.maintenance.MaintenanceDocument;
import cn.fj.roadagent.application.maintenance.MaintenanceSection;
import cn.fj.roadagent.application.port.MaintenanceDocumentRenderer;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PoiMaintenanceDocumentRenderer implements MaintenanceDocumentRenderer {
    @Override
    public byte[] render(MaintenanceDocument document) {
        try (XWPFDocument word = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            configurePage(word);
            title(word, document.title());
            centered(word, "福建普通国省干线养护管理", 12);
            centered(word, "编制年度  " + document.reportYear(), 11);
            centered(word, "生成时间  " + DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
                    .withZone(ZoneId.of("Asia/Shanghai")).format(document.generatedAt()), 10);
            word.createParagraph().setPageBreak(true);
            heading(word, "摘要", 1);
            body(word, document.summary());
            for (MaintenanceSection section : document.sections()) {
                heading(word, section.heading(), 1);
                section.paragraphs().forEach(text -> body(word, text));
                if (!section.rows().isEmpty()) table(word, section.rows());
            }
            if (!document.charts().isEmpty()) {
                heading(word, "图表分析", 1);
                for (MaintenanceChart chart : document.charts()) {
                    XWPFParagraph caption = word.createParagraph();
                    caption.setAlignment(ParagraphAlignment.CENTER);
                    run(caption, chart.title(), 11, true);
                    byte[] image = chartImage(chart);
                    XWPFRun picture = word.createParagraph().createRun();
                    picture.addPicture(new ByteArrayInputStream(image), Document.PICTURE_TYPE_PNG,
                            chart.title() + ".png", Units.toEMU(560), Units.toEMU(280));
                }
            }
            heading(word, "结论与建议", 1);
            body(word, document.conclusion());
            footer(word);
            word.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("生成养护Word文档失败", exception);
        }
    }

    private void configurePage(XWPFDocument word) {
        CTSectPr section = word.getDocument().getBody().isSetSectPr()
                ? word.getDocument().getBody().getSectPr() : word.getDocument().getBody().addNewSectPr();
        CTPageSz size = section.addNewPgSz();
        size.setW(BigInteger.valueOf(12240)); size.setH(BigInteger.valueOf(15840));
        CTPageMar margin = section.addNewPgMar();
        margin.setTop(BigInteger.valueOf(1080)); margin.setBottom(BigInteger.valueOf(1080));
        margin.setLeft(BigInteger.valueOf(1260)); margin.setRight(BigInteger.valueOf(1260));
    }

    private void title(XWPFDocument word, String text) {
        XWPFParagraph p = word.createParagraph();
        p.setStyle("Title"); p.setAlignment(ParagraphAlignment.CENTER); p.setSpacingAfter(360);
        run(p, text, 22, true);
    }

    private void centered(XWPFDocument word, String text, int size) {
        XWPFParagraph p = word.createParagraph(); p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(120); run(p, text, size, false);
    }

    private void heading(XWPFDocument word, String text, int level) {
        XWPFParagraph p = word.createParagraph(); p.setStyle("Heading" + level);
        p.setSpacingBefore(220); p.setSpacingAfter(100); run(p, text, level == 1 ? 15 : 13, true);
    }

    private void body(XWPFDocument word, String text) {
        XWPFParagraph p = word.createParagraph(); p.setIndentationFirstLine(420);
        p.setSpacingBetween(1.45); p.setSpacingAfter(100); run(p, text, 11, false);
    }

    private XWPFRun run(XWPFParagraph p, String text, int size, boolean bold) {
        XWPFRun r = p.createRun(); r.setText(text == null ? "" : text); r.setFontSize(size); r.setBold(bold);
        r.setFontFamily("宋体", XWPFRun.FontCharRange.eastAsia); r.setColor("000000"); return r;
    }

    private void table(XWPFDocument word, List<Map<String, Object>> rows) {
        List<String> headers = new ArrayList<>(rows.get(0).keySet());
        XWPFTable table = word.createTable(rows.size() + 1, headers.size());
        table.setWidth("100%");
        for (int i = 0; i < headers.size(); i++) cell(table.getRow(0).getCell(i), headers.get(i), true, i == 0 ? "D9EAF7" : "D9EAF7");
        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < headers.size(); col++) {
                Object value = rows.get(row).get(headers.get(col));
                cell(table.getRow(row + 1).getCell(col), value == null ? "" : String.valueOf(value), false,
                        row % 2 == 0 ? "FFFFFF" : "F4F8FB");
            }
        }
        table.getCTTbl().addNewTblPr().addNewTblBorders();
        word.createParagraph().setSpacingAfter(100);
    }

    private void cell(XWPFTableCell cell, String text, boolean bold, String fill) {
        cell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.CENTER);
        CTTcPr properties = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        properties.addNewShd().setFill(fill);
        XWPFParagraph p = cell.getParagraphs().get(0); p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingAfter(0); run(p, text, 8, bold);
    }

    private byte[] chartImage(MaintenanceChart chart) throws Exception {
        int width = 1120, height = 560;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE); g.fillRect(0, 0, width, height);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font("Noto Sans CJK SC", Font.PLAIN, 24));
        double max = chart.values().stream().mapToDouble(Double::doubleValue).max().orElse(1);
        int count = Math.max(1, chart.values().size()); int slot = 900 / count;
        for (int i = 0; i < chart.values().size(); i++) {
            int barHeight = (int) (330 * chart.values().get(i) / Math.max(1, max));
            int x = 130 + i * slot + 12; int y = 430 - barHeight;
            g.setColor(new Color(31, 120, 180)); g.fillRoundRect(x, y, Math.max(24, slot - 24), barHeight, 8, 8);
            g.setColor(Color.DARK_GRAY); String label = chart.labels().get(i);
            if (label.length() > 8) label = label.substring(0, 8);
            g.drawString(label, x, 470); g.drawString(String.format("%.1f", chart.values().get(i)), x, Math.max(30, y - 8));
        }
        g.dispose(); ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }

    private void footer(XWPFDocument word) {
        XWPFFooter footer = word.createFooter(HeaderFooterType.DEFAULT);
        XWPFParagraph p = footer.createParagraph(); p.setAlignment(ParagraphAlignment.CENTER);
        run(p, "福建普通国省干线养护管理", 9, false);
    }
}
