package com.gudo.rms.ui.receipts;

import com.gudo.rms.config.AppContext;
import com.gudo.rms.models.Project;
import com.gudo.rms.models.Receipt;
import com.gudo.rms.models.ReceiptMath;
import com.gudo.rms.models.Supplier;
import com.gudo.rms.services.ReceiptQuery;
import com.gudo.rms.ui.RefreshablePanel;
import com.gudo.rms.ui.components.DateField;
import com.gudo.rms.ui.theme.RmsColors;
import com.gudo.rms.utils.BackgroundTask;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SortOrder;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** The searchable receipt directory. Clicking a receipt number opens its detail view. */
public final class ReceiptDirectoryPanel extends JPanel implements RefreshablePanel {
    private record Choice(UUID id, String label) {
        @Override public String toString() { return label; }
    }

    private record Loaded(List<Receipt> receipts, List<Supplier> suppliers, List<Project> projects) { }

    private final AppContext ctx;
    private final Consumer<UUID> onOpen;
    private final JTextField search = new JTextField(22);
    private final DateField from = new DateField();
    private final DateField to = new DateField();
    private final JComboBox<Choice> supplierBox = new JComboBox<>();
    private final JComboBox<Choice> projectBox = new JComboBox<>();
    private final JTable table = new JTable();
    private final JLabel status = new JLabel(" ");
    private final Timer debounce;
    private int requestId;
    private boolean updatingCombos;

    public ReceiptDirectoryPanel(AppContext ctx, Consumer<UUID> onOpen, Runnable onAdd) {
        super(new BorderLayout(0, 12));
        this.ctx = ctx;
        this.onOpen = onOpen;
        setOpaque(false);

        search.putClientProperty("JTextField.placeholderText", "Search receipt no., TIN, supplier, item or project");
        supplierBox.addItem(new Choice(null, "All suppliers"));
        projectBox.addItem(new Choice(null, "All projects"));
        JButton add = new JButton("+ Add Receipt");
        add.setBackground(RmsColors.PRIMARY);
        add.setForeground(java.awt.Color.WHITE);
        add.addActionListener(e -> onAdd.run());
        JButton refresh = new JButton("Refresh");
        refresh.setToolTipText("Load the latest receipts from the shared database");
        refresh.addActionListener(e -> refreshData());
        JButton clear = new JButton("Clear filters");
        clear.addActionListener(e -> {
            search.setText("");
            from.setText("");
            to.setText("");
            supplierBox.setSelectedIndex(0);
            projectBox.setSelectedIndex(0);
            refreshData();
        });

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        JPanel row1 = new JPanel(new BorderLayout(10, 0));
        row1.setOpaque(false);
        row1.add(search, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refresh);
        actions.add(add);
        row1.add(actions, BorderLayout.EAST);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row2.setOpaque(false);
        row2.add(new JLabel("From"));
        row2.add(from);
        row2.add(new JLabel("To"));
        row2.add(to);
        row2.add(supplierBox);
        row2.add(projectBox);
        row2.add(clear);
        top.add(row1, BorderLayout.NORTH);
        top.add(row2, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(false);
        table.getTableHeader().setReorderingAllowed(false);
        add(new JScrollPane(table), BorderLayout.CENTER);
        status.setForeground(RmsColors.SECONDARY_TEXT);
        status.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));
        add(status, BorderLayout.SOUTH);
        installTableBehaviour();
        setModel(new ReceiptTableModel(List.of(), Map.of(), Map.of()));

        debounce = new Timer(250, e -> refreshData());
        debounce.setRepeats(false);
        DocumentListener changed = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        };
        search.getDocument().addDocumentListener(changed);
        from.getDocument().addDocumentListener(changed);
        to.getDocument().addDocumentListener(changed);
        supplierBox.addActionListener(e -> { if (!updatingCombos) refreshData(); });
        projectBox.addActionListener(e -> { if (!updatingCombos) refreshData(); });
        refreshData();
    }

    private void installTableBehaviour() {
        DefaultTableCellRenderer link = new DefaultTableCellRenderer() {
            @Override public java.awt.Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, "<html><u>" + v + "</u></html>", sel, focus, r, c);
                if (!sel) setForeground(RmsColors.PRIMARY);
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0) return;
                boolean onNumber = table.columnAtPoint(e.getPoint()) == 0;
                if (onNumber || e.getClickCount() == 2) open(viewRow);
            }
        });
        table.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                boolean hand = table.rowAtPoint(e.getPoint()) >= 0 && table.columnAtPoint(e.getPoint()) == 0;
                table.setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
        table.putClientProperty("rms.linkRenderer", link);
    }

    private void open(int viewRow) {
        ReceiptTableModel m = (ReceiptTableModel) table.getModel();
        onOpen.accept(m.receiptAt(table.convertRowIndexToModel(viewRow)).id());
    }

    private void setModel(ReceiptTableModel model) {
        table.setModel(model);
        TableRowSorter<ReceiptTableModel> sorter = new TableRowSorter<>(model);
        sorter.setComparator(1, (a, b) -> ReceiptTableModel.parseDate((String) a).compareTo(ReceiptTableModel.parseDate((String) b)));
        sorter.setComparator(4, (a, b) -> ReceiptTableModel.parseMoney((String) a).compareTo(ReceiptTableModel.parseMoney((String) b)));
        sorter.setComparator(5, (a, b) -> Integer.compare(count((String) a), count((String) b)));
        table.setRowSorter(sorter);
        sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(1, SortOrder.DESCENDING)));
        DefaultTableCellRenderer link = (DefaultTableCellRenderer) table.getClientProperty("rms.linkRenderer");
        table.getColumnModel().getColumn(0).setCellRenderer(link);
        int[] widths = {130, 110, 240, 200, 120, 60};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    private static int count(String s) { return ReceiptTableModel.DASH.equals(s) ? 0 : Integer.parseInt(s); }

    /** Reloads receipts using the current search and filters. */
    @Override public void refreshData() {
        if (!from.isValidInput() || !to.isValidInput()) {
            showMessage("Enter dates as YYYY-MM-DD.", true);
            return;
        }
        ReceiptQuery q = new ReceiptQuery(search.getText(), from.value(), to.value(),
                ((Choice) supplierBox.getSelectedItem()).id(), ((Choice) projectBox.getSelectedItem()).id());
        int id = ++requestId;
        showMessage("Loading…", false);
        BackgroundTask.run(() -> new Loaded(ctx.receipts.search(q), ctx.suppliers.findAll(), ctx.projects.findAll()), loaded -> {
            if (id != requestId) return;
            show(loaded);
        }, error -> {
            if (id != requestId) return;
            showMessage(error instanceof RuntimeException && error.getMessage() != null
                    ? error.getMessage() : "Couldn't load receipts. Please try again.", true);
        });
    }

    private void show(Loaded loaded) {
        Map<UUID, Supplier> sup = new HashMap<>();
        loaded.suppliers().forEach(s -> sup.put(s.id(), s));
        Map<UUID, Project> proj = new HashMap<>();
        loaded.projects().forEach(p -> proj.put(p.id(), p));
        fillCombo(supplierBox, "All suppliers", loaded.suppliers().stream().map(s -> new Choice(s.id(), s.businessName())).toList());
        fillCombo(projectBox, "All projects", loaded.projects().stream().map(p -> new Choice(p.id(), p.name())).toList());
        setModel(new ReceiptTableModel(loaded.receipts(), sup, proj));
        BigDecimal total = loaded.receipts().stream().map(Receipt::grandTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (loaded.receipts().isEmpty()) showMessage("No receipts match your search.", false);
        else showMessage(loaded.receipts().size() + " receipt(s) · Total " + ReceiptMath.format(total), false);
    }

    private void fillCombo(JComboBox<Choice> box, String allLabel, List<Choice> choices) {
        UUID selected = ((Choice) box.getSelectedItem()).id();
        updatingCombos = true;
        DefaultComboBoxModel<Choice> model = new DefaultComboBoxModel<>();
        model.addElement(new Choice(null, allLabel));
        choices.forEach(model::addElement);
        box.setModel(model);
        for (int i = 0; i < model.getSize(); i++) if (java.util.Objects.equals(model.getElementAt(i).id(), selected)) box.setSelectedIndex(i);
        updatingCombos = false;
    }

    private void showMessage(String text, boolean error) {
        status.setText(text);
        status.setForeground(error ? RmsColors.DANGER : RmsColors.SECONDARY_TEXT);
    }
}
