package com.sqlai.sql_ia_translator.controller;

import com.sqlai.sql_ia_translator.dto.ConnectionRequestDTO;
import com.sqlai.sql_ia_translator.dto.ConnectionResponseDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.service.ConnectionService;
import com.sqlai.sql_ia_translator.service.SchemaExtractorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.util.List;

@RestController
@RequestMapping("/api/connection")
public class ConnectionController {

    private final ConnectionService connectionService;
    private final SchemaExtractorService schemaExtractorService;

    public ConnectionController(ConnectionService connectionService, SchemaExtractorService schemaExtractorService) {
        this.connectionService = connectionService;
        this.schemaExtractorService = schemaExtractorService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectionResponseDTO connect(@Valid @RequestBody ConnectionRequestDTO request) {
        String connectionId = connectionService.createConnection(request);
        DataSource dataSource = connectionService.getDataSource(connectionId);

        SchemaDTO schema = schemaExtractorService.extractSchema(dataSource);
        connectionService.storeSchema(connectionId, schema);

        List<String> tableNames = schema.tables().stream()
                .map(t -> t.name())
                .toList();

        return new ConnectionResponseDTO(connectionId, tableNames.size(), tableNames);
    }

    @GetMapping("/{connectionId}/schema")
    public SchemaDTO getSchema(@PathVariable String connectionId) {
        return connectionService.getSchema(connectionId);
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable String connectionId) {
        connectionService.removeConnection(connectionId);
    }
}