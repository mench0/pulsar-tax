package am.pulsar.tax.app.controller;

import am.pulsar.tax.app.service.DocumentService;
import am.pulsar.tax.app.service.DocumentService.ConfirmRequest;
import am.pulsar.tax.app.service.DocumentService.DocumentInfo;
import am.pulsar.tax.app.utils.AuthenticationHolder;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Документы: загрузка (PDF, PNG, JPEG до 10 МБ), распознанные реквизиты, подтверждение и связь с операциями. */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DocumentController {

    DocumentService documentService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentInfo upload(@RequestParam("file") MultipartFile file) throws IOException {
        return documentService.upload(business(), file.getOriginalFilename(), file.getBytes());
    }

    @GetMapping
    public List<DocumentInfo> list() {
        return documentService.list(business());
    }

    @GetMapping("/{id}")
    public DocumentInfo get(@PathVariable Long id) {
        return documentService.get(business(), id);
    }

    /** Файл отдаётся вложением и с запретом угадывать тип — содержимое не исполняется в браузере. */
    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        DocumentService.FileContent f = documentService.file(business(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(f.contentType()))
                .body(f.content());
    }

    @PostMapping("/{id}/confirm")
    public DocumentInfo confirm(@PathVariable Long id, @RequestBody ConfirmRequest request) {
        return documentService.confirm(business(), id, request);
    }

    @PostMapping("/{id}/reject")
    public DocumentInfo reject(@PathVariable Long id) {
        return documentService.reject(business(), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        documentService.delete(business(), id);
    }

    private static Long business() {
        return AuthenticationHolder.getCurrentBusinessIdOrElseThrow();
    }
}
