package frontend;

import backend.BinanceRestService;
import backend.BinanceTicker24h;
import backend.CoinGeckoService;
import backend.WsPriceService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CryptoDashboardFrame extends JFrame {
    private volatile String wsStatus = "ожидание";
    private volatile String restStatus = "ожидание";
    private volatile String geckoStatus = "ожидание";


    private final JComboBox<String> coinSelector =
            new JComboBox<>(new String[]{"BTC", "ETH", "SOL", "BNB", "XRP"});

    private final JLabel priceLabel = new JLabel("—", SwingConstants.CENTER);
    private final JTextArea binanceInfoArea = new JTextArea(6, 30);
    private final JTextArea descriptionArea = new JTextArea(8, 30);
    private final JLabel statusLabel = new JLabel("Источники: WS = ожидание, " +
            "REST = ожидание, CoinGecko = ожидание");

    private final WsPriceService wsPriceService;
    private final CoinGeckoService coinGeckoService;
    private final BinanceRestService binanceRestService;

    public CryptoDashboardFrame() {
        super("Crypto Dashboard");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(760, 560));
        setLocationRelativeTo(null);

        this.wsPriceService = new WsPriceService(
                price -> SwingUtilities.invokeLater(() -> setPrice(formatPrice(price))),
                status -> SwingUtilities.invokeLater(() -> {
                    int idx = status.indexOf(':');
                    wsStatus = idx >= 0 ? status.substring(idx + 1) : status;
                    updateStatusBar();
                })
        );

        this.coinGeckoService = new CoinGeckoService(
                (coin, desc) -> SwingUtilities.invokeLater(() ->
                        setDescription(desc == null ? "Описание недоступно." : desc)),
                (coin, status) -> SwingUtilities.invokeLater(() -> {
                    geckoStatus = switch (status) {
                        case "loading" -> "загрузка…";
                        case "ok"      -> "ok";
                        case "error"   -> "ошибка";
                        default        -> status;
                    };
                    updateStatusBar();
                })
        );

        this.binanceRestService = new BinanceRestService(
                (coin, data) -> SwingUtilities.invokeLater(() ->
                        setBinanceInfo(formatBinance24h(data))),
                (coin, status) -> SwingUtilities.invokeLater(() -> {
                    restStatus = switch (status) {
                        case "loading" -> "загрузка…";
                        case "ok"      -> "ok";
                        case "error"   -> "ошибка";
                        default        -> status;
                    };
                    updateStatusBar();
                })
        );

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildCenter(), BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);
        pack();

        updateStatusBar();
        coinSelector.addActionListener(e -> onCoinChanged());

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                wsPriceService.unsubscribe();
                binanceRestService.unsubscribe();
            }
        });

        onCoinChanged();
    }

    private void onCoinChanged() {
        String coin = getSelectedCoin();
        String symbol = coin + "USDT";

        priceLabel.setText("…");
        wsPriceService.subscribe(symbol);

        setDescription("Загрузка описания…");
        coinGeckoService.load(coin);

        setBinanceInfo("Загрузка 24h-статистики…");
        binanceRestService.subscribe(coin);
    }


    private static String formatPrice(double p) {
        if (p >= 100)  return String.format("$%,.2f", p);
        if (p >= 1)    return String.format("$%.4f", p);
        return String.format("$%.6f", p);
    }

    public void setPrice(String text) { priceLabel.setText(text); }
    public void setBinanceInfo(String text) { binanceInfoArea.setText(text); }
    public void setDescription(String text) {
        descriptionArea.setText(text);
        descriptionArea.setCaretPosition(0);
    }
    public void setStatus(String text) { statusLabel.setText(text); }
    public String getSelectedCoin() { return (String) coinSelector.getSelectedItem(); }

    private JPanel buildTopBar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        panel.add(new JLabel("Криптовалюта:"));
        coinSelector.setPreferredSize(new Dimension(120, 28));
        panel.add(coinSelector);
        return panel;
    }

    private JComponent buildCenter() {
        priceLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 42));
        priceLabel.setForeground(new Color(0x2E7D32));
        priceLabel.setBorder(BorderFactory.createTitledBorder("Текущая цена (WebSocket)"));
        priceLabel.setPreferredSize(new Dimension(0, 100));

        binanceInfoArea.setEditable(false);
        binanceInfoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        binanceInfoArea.setLineWrap(true);
        binanceInfoArea.setWrapStyleWord(true);

        descriptionArea.setEditable(false);
        descriptionArea.setLineWrap(true);
        descriptionArea.setWrapStyleWord(true);
        descriptionArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        JPanel textPanels = new JPanel(new GridLayout(1, 2, 10, 0));
        textPanels.add(wrapWithTitle(new JScrollPane(binanceInfoArea), "Инфо 24h (Binance REST)"));
        textPanels.add(wrapWithTitle(new JScrollPane(descriptionArea), "Описание (CoinGecko)"));

        JPanel center = new JPanel(new BorderLayout(10, 10));
        center.add(priceLabel, BorderLayout.NORTH);
        center.add(textPanels, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        panel.add(statusLabel, BorderLayout.WEST);
        return panel;
    }

    private static JPanel wrapWithTitle(JComponent inner, String title) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(inner, BorderLayout.CENTER);
        return p;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new CryptoDashboardFrame().setVisible(true);
        });
    }

    private void updateStatusBar() {
        setStatus("Источники: WS = " + wsStatus
                + " | REST = " + restStatus
                + " | CoinGecko = " + geckoStatus);
    }

    private static String formatBinance24h(BinanceTicker24h t) {
        if (t == null) return "Нет данных";

        StringBuilder sb = new StringBuilder();
        sb.append("Символ:       ").append(t.symbol()).append('\n');
        sb.append("Последняя:    ").append(money(t.lastPrice())).append('\n');
        sb.append("Изм. за 24ч:  ")
                .append(pct(t.priceChangePercent()))
                .append("  (").append(money(t.priceChange())).append(")\n");
        sb.append("High 24h:     ").append(money(t.highPrice())).append('\n');
        sb.append("Low 24h:      ").append(money(t.lowPrice())).append('\n');
        sb.append("Открытие:     ").append(money(t.openPrice())).append('\n');
        sb.append("VWAP:         ").append(money(t.weightedAvgPrice())).append('\n');
        sb.append("Объём:        ").append(volume(t.volume())).append(" монет\n");
        sb.append("Объём ($):    ").append(money(t.quoteVolume())).append('\n');
        return sb.toString();
    }

    private static String money(String s) {
        if (s == null || s.isEmpty()) return "—";
        try {
            double v = Double.parseDouble(s);
            if (Math.abs(v) >= 100)  return String.format("$%,.2f", v);
            if (Math.abs(v) >= 1)    return String.format("$%.4f", v);
            return String.format("$%.6f", v);
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static String pct(String s) {
        if (s == null || s.isEmpty()) return "—";
        try {
            double v = Double.parseDouble(s);
            return String.format("%+.2f%%", v);    // +1.23% / -0.45%
        } catch (NumberFormatException e) {
            return s;
        }
    }

    private static String volume(String s) {
        if (s == null || s.isEmpty()) return "—";
        try {
            double v = Double.parseDouble(s);
            if (v >= 1_000_000) return String.format("%,.2f", v / 1_000_000) + "M";
            if (v >= 1_000)     return String.format("%,.2f", v / 1_000) + "K";
            return String.format("%,.4f", v);
        } catch (NumberFormatException e) {
            return s;
        }
    }

}