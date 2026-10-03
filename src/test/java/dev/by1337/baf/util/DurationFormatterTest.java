package dev.by1337.baf.util;

import dev.by1337.yaml.YamlMap;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class DurationFormatterTest {
    @Test
    void secondsAreFormattedWithTheTwoLargestUnitsAndEveryKeyExistsInLang() throws Exception {
        Map<Long, String> cases = Map.ofEntries(
                Map.entry(-1L, "seconds:0"), Map.entry(0L, "seconds:0"), Map.entry(59L, "seconds:59"),
                Map.entry(60L, "minutes:1"), Map.entry(61L, "minutes.seconds:1:1"),
                Map.entry(3599L, "minutes.seconds:59:59"), Map.entry(3600L, "hours:1"),
                Map.entry(3661L, "hours.minutes:1:1"), Map.entry(86400L, "days:1"),
                Map.entry(90000L, "days.hours:1:1"), Map.entry(2592000L, "months:1"),
                Map.entry(2678400L, "months.days:1:1"), Map.entry(31104000L, "years:1"),
                Map.entry(33696000L, "years.months:1:1"));
        try (var input = getClass().getResourceAsStream("/lang.yml")) {
            assertNotNull(input);
            // Use the same dot protection as LangTranslator so scalar keys and their suffixes coexist.
            var yaml = YamlMap.loadFromString(new String(input.readAllBytes(), StandardCharsets.UTF_8).replace(".", "__dot__"));
            yaml.deepReplace(text -> text.replace("__dot__", "."));
            var lang = (Map<?, ?>) yaml.getRaw("ru_RU");
            for (var entry : cases.entrySet()) {
                assertEquals("<lang:bairdropflow.time.format." + entry.getValue() + ">", DurationFormatter.formatSeconds(entry.getKey()));
                assertTrue(lang.containsKey(("time.format." + entry.getValue().split(":")[0]).replace(".", "__dot__")), entry.getValue());
            }
        }
    }
}
