package com.sqlai.sql_ia_translator.service;

import com.sqlai.sql_ia_translator.dto.ColumnDTO;
import com.sqlai.sql_ia_translator.dto.ForeignKeyDTO;
import com.sqlai.sql_ia_translator.dto.SchemaDTO;
import com.sqlai.sql_ia_translator.dto.TableDTO;
import com.sqlai.sql_ia_translator.exception.DatabaseConnectionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SchemaExtractorService {

    private static final Logger log = LoggerFactory.getLogger(SchemaExtractorService.class);

    public SchemaDTO extractSchema(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();

            List<String> tableNames = extractTableNames(metaData, catalog, schema);
            List<TableDTO> tables = new ArrayList<>();

            for (String tableName : tableNames) {
                List<ColumnDTO> columns = extractColumns(metaData, catalog, schema, tableName);
                List<String> primaryKeys = extractPrimaryKeys(metaData, catalog, schema, tableName);
                List<ForeignKeyDTO> foreignKeys = extractForeignKeys(metaData, catalog, schema, tableName);
                tables.add(new TableDTO(tableName, columns, primaryKeys, foreignKeys));
            }

            return new SchemaDTO(tables);
        } catch (SQLException e) {
            log.error("Error al extraer el esquema (SQLState: {})", e.getSQLState(), e);
            throw new DatabaseConnectionException("Error al extraer el esquema de la base de datos");
        }
    }

    private List<String> extractTableNames(DatabaseMetaData metaData, String catalog, String schema)
            throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet rs = metaData.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                names.add(rs.getString("TABLE_NAME"));
            }
        }
        return names;
    }

    private List<ColumnDTO> extractColumns(DatabaseMetaData metaData, String catalog, String schema, String table)
            throws SQLException {
        List<ColumnDTO> columns = new ArrayList<>();
        try (ResultSet rs = metaData.getColumns(catalog, schema, table, "%")) {
            while (rs.next()) {
                columns.add(new ColumnDTO(
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable
                ));
            }
        }
        return columns;
    }

    private List<String> extractPrimaryKeys(DatabaseMetaData metaData, String catalog, String schema, String table)
            throws SQLException {
        List<String> keys = new ArrayList<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                keys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return keys;
    }

    private List<ForeignKeyDTO> extractForeignKeys(DatabaseMetaData metaData, String catalog, String schema, String table)
            throws SQLException {
        List<ForeignKeyDTO> keys = new ArrayList<>();
        try (ResultSet rs = metaData.getImportedKeys(catalog, schema, table)) {
            while (rs.next()) {
                keys.add(new ForeignKeyDTO(
                        rs.getString("FKCOLUMN_NAME"),
                        rs.getString("PKTABLE_NAME"),
                        rs.getString("PKCOLUMN_NAME")
                ));
            }
        }
        return keys;
    }
}