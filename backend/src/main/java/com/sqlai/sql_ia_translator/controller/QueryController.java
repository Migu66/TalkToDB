package com.sqlai.sql_ia_translator.controller;

import com.sqlai.sql_ia_translator.dto.NaturalLanguageQueryDTO;
import com.sqlai.sql_ia_translator.dto.QueryResultDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.service.ConnectionService;
import com.sqlai.sql_ia_translator.service.OpenAiService;
import com.sqlai.sql_ia_translator.service.QueryExecutionService;
import com.sqlai.sql_ia_translator.service.SqlValidatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private final ConnectionService connectionService;
    private final OpenAiService openAiService;
    private final SqlValidatorService sqlValidatorService;
    private final QueryExecutionService queryExecutionService;

    public QueryController(ConnectionService connectionService,
                           OpenAiService openAiService,
                           SqlValidatorService sqlValidatorService,
                           QueryExecutionService queryExecutionService) {
        this.connectionService = connectionService;
        this.openAiService = openAiService;
        this.sqlValidatorService = sqlValidatorService;
        this.queryExecutionService = queryExecutionService;
    }

    @PostMapping
    public QueryResultDTO query(@Valid @RequestBody NaturalLanguageQueryDTO request) {
        SchemaDTO schema = connectionService.getSchema(request.connectionId());
        DataSource dataSource = connectionService.getDataSource(request.connectionId());

        String generatedSql = openAiService.generateSql(schema, request.question());
        sqlValidatorService.validate(generatedSql);
        sqlValidatorService.validateAgainstSchema(generatedSql, schema);

        return queryExecutionService.execute(dataSource, generatedSql);
    }
}
