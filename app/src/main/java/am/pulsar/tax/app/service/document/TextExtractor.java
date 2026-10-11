package am.pulsar.tax.app.service.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Текст документа без внешних сервисов: текстовый слой PDF (PDFBox) или распознавание изображения
 * локальным tesseract, если путь к нему задан (pulsar.ocr.tesseract). Файлы не покидают сервер.
 */
@Component
public class TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TextExtractor.class);
    /** PDF с текстовым слоем короче этого считается сканом без текста. */
    static final int MIN_TEXT = 20;

    public record Result(String method, String text) {
    }

    private final String tesseract;

    public TextExtractor(@Value("${pulsar.ocr.tesseract:}") String tesseract) {
        this.tesseract = tesseract == null ? "" : tesseract.strip();
    }

    public boolean ocrAvailable() {
        return !tesseract.isEmpty() && new File(tesseract).canExecute();
    }

    public Result extract(byte[] content, String contentType) {
        try {
            if ("application/pdf".equals(contentType)) {
                try (PDDocument pdf = Loader.loadPDF(content)) {
                    if (pdf.isEncrypted()) return new Result("FAILED", "");
                    String text = new PDFTextStripper().getText(pdf);
                    if (text.strip().length() >= MIN_TEXT) return new Result("TEXT_LAYER", text);
                    // Скан в PDF: без растеризации страниц распознать нельзя — пользователь вводит данные сам.
                    return new Result("NO_TEXT", text);
                }
            }
            if (!ocrAvailable()) return new Result("OCR_UNAVAILABLE", "");
            return new Result("OCR", ocr(content, contentType));
        } catch (Exception e) {
            log.warn("Не удалось извлечь текст документа: {}", e.toString());
            return new Result("FAILED", "");
        }
    }

    private String ocr(byte[] content, String contentType) throws Exception {
        Path dir = Files.createTempDirectory("pulsar-ocr");
        try {
            Path in = dir.resolve(contentType.equals("image/png") ? "doc.png" : "doc.jpg");
            Files.write(in, content);
            List<String> cmd = new ArrayList<>(List.of(tesseract, in.toString(), dir.resolve("out").toString(), "-l", languages()));
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(dir.resolve("log.txt").toFile()).start();
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IllegalStateException("tesseract: превышено время");
            }
            if (p.exitValue() != 0) throw new IllegalStateException("tesseract: код " + p.exitValue());
            return Files.readString(dir.resolve("out.txt"), StandardCharsets.UTF_8);
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
            }
        }
    }

    /** Армянский, русский и английский — из установленных у tesseract. */
    private String languages() {
        try {
            Process p = new ProcessBuilder(tesseract, "--list-langs").redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor(10, TimeUnit.SECONDS);
            List<String> langs = new ArrayList<>();
            for (String l : List.of("hye", "rus", "eng")) if (out.lines().anyMatch(x -> x.strip().equals(l))) langs.add(l);
            return langs.isEmpty() ? "eng" : String.join("+", langs);
        } catch (Exception e) {
            return "eng";
        }
    }
}
