package com.bred.rms.ui.reports;

import com.bred.rms.config.AppContext;
import com.bred.rms.models.Receipt;
import com.bred.rms.models.ReceiptMath;
import com.bred.rms.services.ReportService.Total;
import com.bred.rms.ui.RefreshablePanel;
import com.bred.rms.ui.UiErrors;
import com.bred.rms.ui.components.DateField;
import com.bred.rms.ui.theme.RmsColors;
import com.bred.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Totals by project, supplier or month for a date range, with CSV export of the underlying receipts. */
public final class ReportsPanel extends JPanel implements RefreshablePanel {
    private static final String BY_PROJECT = "By project";
    private static final String BY_SUPPLIER = "By supplier";
    private static final String BY_MONTH = "By month";

    private final AppContext ctx;
    private final DateField from = new DateField();
    private final DateField to = new DateField();
    private final JComboBox<String> groupBy = new JComboBox<>(new String[]{BY_PROJECT, BY_SUPPLIER, BY_MONTH});
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Group", "Receipts", "Total"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel status = new JLabel(" ");
    private final JButton export = new JButton("Export CSV");
    private int requestId;

    /** Builds the panel and runs the default report. */
    public ReportsPanel(AppContext ctx) {
        super(new BorderLayout(0, 10));
        this.ctx = ctx;
        JButton run = new JButton("Show");
        run.addActionListener(e -> refreshData());
        export.addActionListener(e -> exportCsv());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.add(new JLabel("From"));
        bar.add(from);
        bar.add(new JLabel("To"));
        bar.add(to);
        bar.add(groupBy);
        bar.add(run);
        bar.add(export);
        add(bar, BorderLayout.NORTH);
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        add(new JScrollPane(table), BorderLayout.CENTER);
        status.setForeground(RmsColors.SECONDARY_TEXT);
        status.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        add(status, BorderLayout.SOUTH);
        groupBy.addActionListener(e -> refreshData());
        refreshData();
    }

    /** Returns the chosen range, or null (after showing a message) when a date is malformed. */
    private LocalDate[] range() {
        if (!from.isValidInput() || !to.isValidInput()) {
            status.setText("Enter dates as YYYY-MM-DD, or leave them empty.");
            return null;
        }
        LocalDate f = from.value();
        LocalDate t = to.value();
        if (f != null && t != null && f.isAfter(t)) {
            status.setText("The start date is after the end date.");
            return null;
        }
        return new LocalDate[]{f, t};
    }

    @Override
    public void refreshData() {
        LocalDate[] r = range();
        if (r == null) return;
        String kind = (String) groupBy.getSelectedItem();
        int my = ++requestId;
        status.setText("Loading…");
        BackgroundTask.run(() -> switch (kind == null ? BY_PROJECT : kind) {
            case BY_SUPPLIER -> ctx.reports.totalsBySupplier(r[0], r[1]);
            case BY_MONTH -> ctx.reports.totalsByMonth(r[0], r[1]);
            default -> ctx.reports.totalsByProject(r[0], r[1]);
        }, totals -> { if (my == requestId) fill(totals); }, e -> { if (my == requestId) status.setText(UiErrors.friendly(e)); });
    }

    private void fill(List<Total> totals) {
        model.setRowCount(0);
        int count = 0;
        BigDecimal sum = BigDecimal.ZERO;
        for (Total t : totals) {
            model.addRow(new Object[]{t.label(), t.receiptCount(), ReceiptMath.format(t.amount())});
            count += t.receiptCount();
            sum = sum.add(t.amount());
        }
        status.setText(count + " receipt(s), total " + ReceiptMath.format(sum));
    }

    private void exportCsv() {
        LocalDate[] r = range();
        if (r == null) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("receipts.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File target = chooser.getSelectedFile();
        export.setEnabled(false);
        BackgroundTask.run(() -> {
            List<Receipt> receipts = ctx.reports.receipts(r[0], r[1]);
            ctx.reports.exportCsv(receipts, target.toPath());
            return receipts.size();
        }, n -> {
            export.setEnabled(true);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), n + " receipt(s) exported to\n" + target.getAbsolutePath(),
                    "Export complete", JOptionPane.INFORMATION_MESSAGE);
        }, e -> {
            export.setEnabled(true);
            status.setText(e instanceof java.io.IOException ? "Could not write the file: " + e.getMessage() : UiErrors.friendly(e));
        });
    }
}
