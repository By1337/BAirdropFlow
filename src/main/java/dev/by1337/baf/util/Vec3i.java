package dev.by1337.baf.util;

import dev.by1337.yaml.codec.InlineYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;

public record Vec3i(int x, int y, int z) {
   public static final YamlCodec<Vec3i> CODEC = InlineYamlCodecBuilder.inline(
           ";",
           "<x>;<y>;<z>",
           Vec3i::new,
           YamlCodec.INT.withGetter(Vec3i::x),
           YamlCodec.INT.withGetter(Vec3i::y),
           YamlCodec.INT.withGetter(Vec3i::z)
   );

}
