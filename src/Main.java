import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.File;
import java.nio.ByteBuffer;


public class Main extends JFrame implements ActionListener {

    private final ImagePanel modifiedImagePanel = new ImagePanel();

    private final ImagePanel imagePanel = new ImagePanel();

    private static final int bytesForTextLengthData = 4;

    private static final int bitsInByte = 8;

    private final JToggleButton encodeToggle;

    private final JToggleButton decodeToggle;

    private final JTextArea textArea;

    private final JButton chooseFileButton;

    private final JButton submitButton;
    int f = -1;
    String fileSelected = null;

    public Main() {
        super("LSB");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // Create components
        encodeToggle = new JToggleButton("Кодирование");
        decodeToggle = new JToggleButton("Декодирование");
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(encodeToggle);
        buttonGroup.add(decodeToggle);
        textArea = new JTextArea(15, 30);
        JScrollPane scrollPane = new JScrollPane(textArea);
        chooseFileButton = new JButton("Выберите файл");
        submitButton = new JButton("Преобразовать");
        JPanel contentPane = new JPanel(new BorderLayout());
        setContentPane(contentPane);
        JPanel topPanel = new JPanel();
        topPanel.add(encodeToggle);
        topPanel.add(decodeToggle);
        contentPane.add(topPanel, BorderLayout.NORTH);
        contentPane.add(scrollPane, BorderLayout.EAST);
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(chooseFileButton);
        bottomPanel.add(submitButton);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);
        encodeToggle.addActionListener(this);
        decodeToggle.addActionListener(this);
        chooseFileButton.addActionListener(this);
        submitButton.addActionListener(this);
        setSize(1200, 600);
        setLocationRelativeTo(null);
        setVisible(true);
        contentPane.add(imagePanel, BorderLayout.CENTER);
        JPanel centerPanel = new JPanel(new GridLayout(1, 2));
        centerPanel.add(imagePanel);
        centerPanel.add(modifiedImagePanel);
        contentPane.add(centerPanel, BorderLayout.CENTER);
    }

    public void actionPerformed(ActionEvent e) {
        String text = textArea.getText();

        if (e.getSource() == encodeToggle) {
            if (encodeToggle.isSelected()) {
                f = 0;
                decodeToggle.setSelected(false);
            } else {
                encodeToggle.setSelected(true);
            }
        } else if (e.getSource() == decodeToggle) {
            if (decodeToggle.isSelected()) {
                f = 1;
                encodeToggle.setSelected(false);
            } else {
                decodeToggle.setSelected(true);
            }
        } else if (e.getSource() == chooseFileButton) {
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(this);

            if (result == JFileChooser.APPROVE_OPTION) {
                fileSelected = fileChooser.getSelectedFile().getPath();
                fileSelected = fileChooser.getSelectedFile().getPath();
                BufferedImage image = getImageFromPath(fileSelected);
                imagePanel.setImage(image);
                BufferedImage modifiedImage = new BufferedImage(
                        image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = modifiedImage.createGraphics();
                g2d.drawImage(image, 0, 0, null);
                g2d.dispose();

                for (int y = 0; y < modifiedImage.getHeight(); y++) {
                    for (int x = 0; x < modifiedImage.getWidth(); x++) {
                        int rgb = modifiedImage.getRGB(x, y);
                        rgb &= 0xFFFEFFFE;
                        modifiedImage.setRGB(x, y, rgb);
                    }
                }

                modifiedImagePanel.setImage(modifiedImage);
            }
        }

        if (e.getSource() == submitButton) {
            if (f == 0) {
                encode(fileSelected, text);
            } else if (f == 1) {
                textArea.setText(decode(fileSelected));
            }
        }
    }
    // Encode
    private static void encode(String imagePath, String text) {
        BufferedImage originalImage = getImageFromPath(imagePath);
        BufferedImage imageInUserSpace = getImageInUserSpace(originalImage);
        byte[] imageInBytes = getBytesFromImage(imageInUserSpace);
        byte[] textInBytes = text.getBytes();
        byte[] textLengthInBytes = getBytesFromInt(textInBytes.length);
        try {
            encodeImage(imageInBytes, textLengthInBytes, 0);
            encodeImage(
                    imageInBytes, textInBytes, bytesForTextLengthData * bitsInByte);
        } catch (Exception exception) {
            System.out.println(
                    "Не удалось скрыть текст в изображении : " + exception);
            return;
        }

        String fileName = imagePath;
        int position = fileName.lastIndexOf(".");

        if (position > 0) {
            fileName = fileName.substring(0, position);
        }

        String finalFileName = fileName + "_secret.bmp";
        System.out.println("Успешно закодирован текст в: " + finalFileName);
        saveImageToPath(imageInUserSpace, new File(finalFileName));
    }

    private static void encodeImage(byte[] image, byte[] addition, int offset) {
        if (addition.length + offset > image.length) {
            throw new IllegalArgumentException(
                    "Файл изображения недостаточно длинный для хранения предоставленного текста");
        }

        for (int additionByte: addition) {
            for (int bit = bitsInByte - 1; bit >= 0; --bit, offset++) {
                int b = (additionByte >>> bit) & 0x1;
                image[offset] = (byte)((image[offset] & 0xFE) | b);
            }
        }
    }
    // Decode
    private static String decode(String imagePath) {
        byte[] decodedHiddenText;
        try {
            BufferedImage imageFromPath = getImageFromPath(imagePath);
            BufferedImage imageInUserSpace = getImageInUserSpace(imageFromPath);
            byte[] imageInBytes = getBytesFromImage(imageInUserSpace);
            decodedHiddenText = decodeImage(imageInBytes);
            String hiddenText;
            hiddenText = new String(decodedHiddenText);
            return hiddenText;
        } catch (Exception exception) {
            return "Нет скрытого сообщения ";
        }
    }

    private static byte[] decodeImage(byte[] image) {
        int length = 0;
        int offset = bytesForTextLengthData * bitsInByte;

        for (int i = 0; i < offset; i++) {
            length = (length<< 1) | (image[i] & 0x1);
        }

        byte[] result = new byte[length];

        for (int b = 0; b < result.length; b++) {
            for (int i = 0; i < bitsInByte; i++, offset++) {
                result[b] = (byte)((result[b]<< 1) | (image[offset] & 0x1));
            }
        }

        return result;
    }
    // File I/O methods
    private static void saveImageToPath(BufferedImage image, File file) {
        try {
            ImageIO.write(image, "bmp", file);
        } catch (Exception exception) {
            System.out.println(
                    "Файл изображения не удалось сохранить : " + exception);
        }
    }

    private static BufferedImage getImageFromPath(String path) {
        BufferedImage image = null;
        File file = new File(path);
        try {
            image = ImageIO.read(file);
        } catch (Exception exception) {
            System.out.println(
                    "Изображение не может быть прочитано. Ошибка: " + exception);
        }

        return image;
    }
    // Helpers
    private static BufferedImage getImageInUserSpace(BufferedImage image) {
        BufferedImage imageInUserSpace = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D graphics = imageInUserSpace.createGraphics();
        graphics.drawRenderedImage(image, null);
        graphics.dispose();
        return imageInUserSpace;
    }

    private static byte[] getBytesFromImage(BufferedImage image) {
        WritableRaster raster = image.getRaster();
        DataBufferByte buffer = (DataBufferByte) raster.getDataBuffer();
        return buffer.getData();
    }

    private static byte[] getBytesFromInt(int integer) {
        return ByteBuffer.allocate(bytesForTextLengthData).putInt(integer).array();
    }

    public static void main(String[] args) {
        new Main();
    }
}