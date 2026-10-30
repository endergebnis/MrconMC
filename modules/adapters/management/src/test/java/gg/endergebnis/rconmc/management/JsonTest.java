package gg.endergebnis.rconmc.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void parsesNestedStructures() {
        Object parsed = Json.parse(" {\"id\": 7, \"result\": [{\"name\":\"jeb_\",\"id\":\"853c\"}], \"ok\": true, \"x\": null} ");

        assertThat(parsed).isInstanceOf(Map.class);
        Map<?, ?> map = (Map<?, ?>) parsed;
        assertThat(((Number) map.get("id")).longValue()).isEqualTo(7L);
        assertThat(map.get("ok")).isEqualTo(true);
        assertThat(map.get("x")).isNull();
        assertThat(((List<?>) map.get("result")).get(0)).isEqualTo(Map.of("name", "jeb_", "id", "853c"));
    }

    @Test
    void writesAndEscapesStrings() {
        String json = Json.write(Map.of("name", "a\"b\\c\n\u0001ü"));

        assertThat(json).isEqualTo("{\"name\":\"a\\\"b\\\\c\\n\\u0001ü\"}");
        assertThat(Json.parse(json)).isEqualTo(Map.of("name", "a\"b\\c\n\u0001ü"));
    }

    @Test
    void parsesUnicodeEscapes() {
        assertThat(Json.parse("\"\\u00fcber\"")).isEqualTo("über");
    }

    @Test
    void rejectsMalformedInput() {
        assertThatThrownBy(() -> Json.parse("{\"a\":")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.parse("[1,2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.parse("{} x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Json.parse("[".repeat(200))).isInstanceOf(IllegalArgumentException.class);
    }
}
