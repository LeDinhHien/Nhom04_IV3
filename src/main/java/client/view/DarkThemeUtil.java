package client.view;

import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * Thiết lập giao diện nền tối (FlatLaf Dark) và bảng màu chuẩn hiện đại cho toàn bộ ứng dụng.
 */
public final class DarkThemeUtil {

    // Bảng màu chủ đạo (Dark Palette)
    public static final Color BG_PRIMARY = Color.decode("#1E1F22");
    public static final Color BG_SECONDARY = Color.decode("#2B2D31");
    public static final Color BG_TERTIARY = Color.decode("#232428");
    public static final Color BG_INPUT = Color.decode("#383A40");
    public static final Color BG_HOVER = Color.decode("#35373C");

    public static final Color TEXT_PRIMARY = Color.decode("#F2F3F5");
    public static final Color TEXT_MUTED = Color.decode("#949BA4");

    public static final Color ACCENT_PRIMARY = Color.decode("#5865F2");
    public static final Color ACCENT_SUCCESS = Color.decode("#23A55A");
    public static final Color ACCENT_DANGER = Color.decode("#F23F43");
    public static final Color ACCENT_WARNING = Color.decode("#F0B232");
    public static final Color BORDER_SUBTLE = Color.decode("#3F4147");

    // Font chữ chuẩn Segoe UI
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_SUBTITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_REGULAR = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 13);

    private DarkThemeUtil() {
    }

    /**
     * Kích hoạt FlatDarkLaf và cấu hình thuộc tính bo tròn, màu sắc mặc định cho Swing.
     */
    public static void applyDarkTheme() {
        try {
            FlatDarkLaf.setup();
            UIManager.put("defaultFont", FONT_REGULAR);
            UIManager.put("Panel.background", BG_PRIMARY);
            UIManager.put("OptionPane.background", BG_SECONDARY);
            UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
            UIManager.put("TextField.background", BG_INPUT);
            UIManager.put("TextField.foreground", TEXT_PRIMARY);
            UIManager.put("TextField.caretForeground", TEXT_PRIMARY);
            UIManager.put("PasswordField.background", BG_INPUT);
            UIManager.put("PasswordField.foreground", TEXT_PRIMARY);
            UIManager.put("PasswordField.caretForeground", TEXT_PRIMARY);
            UIManager.put("Table.background", BG_SECONDARY);
            UIManager.put("Table.foreground", TEXT_PRIMARY);
            UIManager.put("Table.selectionBackground", ACCENT_PRIMARY);
            UIManager.put("Table.selectionForeground", Color.WHITE);
            UIManager.put("Table.gridColor", BORDER_SUBTLE);
            UIManager.put("TableHeader.background", BG_TERTIARY);
            UIManager.put("TableHeader.foreground", TEXT_PRIMARY);
            UIManager.put("ScrollPane.background", BG_PRIMARY);
            UIManager.put("Component.arc", 10);
            UIManager.put("Button.arc", 10);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ProgressBar.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 999);
            UIManager.put("ScrollBar.width", 10);
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Định dạng nút bấm bo tròn với màu nền chỉ định.
     */
    public static JButton createStyledButton(String text, Color bgColor, Color fgColor) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(bgColor);
        btn.setForeground(fgColor);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        btn.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        return btn;
    }

    /**
     * Định dạng ô nhập văn bản bo tròn trên nền tối.
     */
    public static void styleTextField(JTextField field, String placeholder) {
        field.setFont(FONT_REGULAR);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.putClientProperty("JComponent.roundRect", true);
        if (placeholder != null && !placeholder.isEmpty()) {
            field.putClientProperty("JTextField.placeholderText", placeholder);
        }
        Border line = BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true);
        Border pad = BorderFactory.createEmptyBorder(8, 12, 8, 12);
        field.setBorder(BorderFactory.createCompoundBorder(line, pad));
    }

    /**
     * Định dạng ô nhập mật khẩu bo tròn trên nền tối.
     */
    public static void stylePasswordField(JPasswordField field, String placeholder) {
        field.setFont(FONT_REGULAR);
        field.setBackground(BG_INPUT);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.putClientProperty("JComponent.roundRect", true);
        field.putClientProperty("FlatLaf.style", "showRevealButton: true");
        if (placeholder != null && !placeholder.isEmpty()) {
            field.putClientProperty("JTextField.placeholderText", placeholder);
        }
        Border line = BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true);
        Border pad = BorderFactory.createEmptyBorder(8, 12, 8, 12);
        field.setBorder(BorderFactory.createCompoundBorder(line, pad));
    }

    /**
     * Áp dụng viền thẻ (Card border) bo góc tinh tế.
     */
    public static void applyCardStyle(JComponent component, Color background, int padding) {
        component.setBackground(background);
        Border line = BorderFactory.createLineBorder(BORDER_SUBTLE, 1, true);
        Border pad = BorderFactory.createEmptyBorder(padding, padding, padding, padding);
        component.setBorder(BorderFactory.createCompoundBorder(line, pad));
    }
}
