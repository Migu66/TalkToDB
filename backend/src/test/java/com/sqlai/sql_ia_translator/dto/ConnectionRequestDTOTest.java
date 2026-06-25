package com.sqlai.sql_ia_translator.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionRequestDTOTest {

    @Test
    void toStringMasksPassword() {
        var dto = new ConnectionRequestDTO("jdbc:h2:mem:test", "sa", "superSecreto");
        String str = dto.toString();

        assertFalse(str.contains("superSecreto"), "El toString no debe revelar la contraseña");
        assertTrue(str.contains("password=***"));
        assertTrue(str.contains("jdbc:h2:mem:test"));
        assertTrue(str.contains("username=sa"));
    }

    @Test
    void toStringMarksEmptyPasswordAsNone() {
        assertTrue(new ConnectionRequestDTO("jdbc:h2:mem:test", "sa", "").toString().contains("password=[none]"));
        assertTrue(new ConnectionRequestDTO("jdbc:h2:mem:test", "sa", null).toString().contains("password=[none]"));
    }

    @Test
    void accessorStillReturnsRealPassword() {
        var dto = new ConnectionRequestDTO("jdbc:h2:mem:test", "sa", "superSecreto");
        assertEquals("superSecreto", dto.password());
    }
}
