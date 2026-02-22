package dev.by1337.baf.util;

import dev.by1337.yaml.codec.InlineYamlCodecBuilder;
import dev.by1337.yaml.codec.YamlCodec;

public record Vec3d(double x, double y, double z) {
   public static final YamlCodec<Vec3d> CODEC = InlineYamlCodecBuilder.inline(
           ";",
           "<x>;<y>;<z>",
           Vec3d::new,
           YamlCodec.DOUBLE.withGetter(Vec3d::x),
           YamlCodec.DOUBLE.withGetter(Vec3d::y),
           YamlCodec.DOUBLE.withGetter(Vec3d::z)
   );

}
