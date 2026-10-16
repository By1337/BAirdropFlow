# API и аддоны BAirdropFlow

Аддоны загружаются из `plugins/BAirdropFlow/addons/*.jar`, по модели BAuctionV2. Это обычные JAR с одним главным классом, который наследует `AbstractAddon` и отмечен `@BAFAddon`. Отдельный `plugin.yml` не нужен.

Зависимость для сборки:

```xml
<dependency>
    <groupId>dev.by1337.baf</groupId>
    <artifactId>BAirdropFlow</artifactId>
    <version>1.0</version>
    <scope>provided</scope>
</dependency>
```

Используйте Java 21 или совместимую с сервером версию. BAirdropFlow, BLibV2 и их API не нужно включать внутрь JAR аддона. Если используете shading для собственных библиотек, перемещайте их пакеты, чтобы избежать конфликтов с библиотеками плагина.

## Порядок загрузки

1. BAirdropFlow регистрирует встроенные компоненты, генераторы и спавнеры.
2. Загрузчик находит JAR аддонов в порядке имён файлов и создаёт экземпляры главных классов.
3. Для каждого аддона вызываются `onLoad()`, `componentsRegister(...)`, `generatorsRegister(...)`, `spawnersRegister(...)`, `globalSpawnersRegister(...)`.
4. Плагин читает workflow, загружает меню и глобальные спавнеры, поднимает runtime-сервисы.
5. Вызывается `onEnable()` аддонов.

Регистрируйте типы в регистрационных хуках: в `onEnable()` конфиги уже прочитаны. В конструкторе аддона контекст ещё недоступен. В `onLoad()` и регистрационных хуках runtime-сервисы также ещё не готовы; работать с ними можно в `onEnable()`.

При отключении сначала очищаются активные события и ресурсы плагина, затем в обратном порядке вызывается `onDisable()` аддонов и закрываются их загрузчики классов. Освобождайте собственные задачи, слушатели и ресурсы в `onDisable()`. Этот хук может прийти без `onEnable()`, например после ошибки регистрации: учитывайте частичную инициализацию.

`/baf reload` завершает текущий цикл и создаёт новые экземпляры аддонов с новыми загрузчиками классов. Встроенные реестры пересоздаются по содержимому, после чего хуки регистрации вызываются снова. Не храните старые объекты плагина между циклами.

Некорректные JAR и повторяющиеся имена аддонов пропускаются с ошибкой в логе. Ошибка регистрационного хука или `onEnable()` прерывает запуск плагина, чтобы сценарии не работали с частично инициализированным аддоном. Имена аддонов: латинские буквы, цифры, `_` и `-`; имена должны быть уникальны без учёта регистра.

## Пример: собственный генератор

Главный класс с публичным конструктором без аргументов:

```java
package example;

import dev.by1337.baf.api.addon.AbstractAddon;
import dev.by1337.baf.api.addon.BAFAddon;
import dev.by1337.baf.generator.Generator;
import dev.by1337.baf.registry.SchemaRegistry;
import dev.by1337.baf.web.NodeSchema;

@BAFAddon(name = "Example", version = "1.0", author = "YourName")
public final class ExampleAddon extends AbstractAddon {
    @Override
    public void generatorsRegister(SchemaRegistry<Generator> registry) {
        registry.register("example:high_point", HighPoint.DECODER,
                NodeSchema.of("Высокая точка", "Точка 0;Y;0 в мире сценария")
                        .integer("y", "Высота", 120, -64, false));
    }

    @Override
    public void onEnable() {
        getLogger().info("Аддон включён");
    }
}
```

Реализация генератора:

```java
package example;

import dev.by1337.baf.generator.Generator;
import dev.by1337.yaml.decoder.RecordYamlDecoder;
import dev.by1337.yaml.decoder.YamlDecoder;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.concurrent.CompletableFuture;

public record HighPoint(int y) implements Generator {
    public static final YamlDecoder<HighPoint> DECODER = RecordYamlDecoder.mapOf(
            HighPoint::new, YamlDecoder.INT.fieldOf("y", 120));

    @Override
    public CompletableFuture<Location> tryGenerate(World world) {
        return CompletableFuture.completedFuture(new Location(world, 0, y, 0));
    }
}
```

После установки JAR тип доступен в YAML и веб-редакторе:

```yaml
generator:
  type: example:high_point
  y: 150
```

## Хуки регистрации

Все хуки объявлены в `dev.by1337.baf.api.AirdropLifecycle`, который реализует `AbstractAddon`. Переопределяйте только нужные методы.

| Хук | Что регистрирует | Вызов регистрации |
| --- | --- | --- |
| `componentsRegister(NodeRegistry registry)` | Типы компонентов в `nodes` | `registry.register(id, decoder, schema)` |
| `generatorsRegister(SchemaRegistry<Generator> registry)` | Генераторы точки | `registry.register(id, decoder, schema)` |
| `spawnersRegister(SchemaRegistry<AirdropSpawner> registry)` | Спавнеры в секции `spawner` workflow | `registry.register(id, decoder, schema)` |
| `globalSpawnersRegister(DecoderRegistry<GlobalSpawner> registry)` | Глобальные спавнеры из `spawner/*.yml` | `registry.register(id, decoder)` |

Используйте собственное пространство имён, например `example:storm`. Не вызывайте `boot()` или `clear()` из аддона: это удалит типы плагина и других аддонов. Реестры допускают замену существующего ID, поэтому не используйте чужие ID без намерения заменить их.

Для компонента декодер должен возвращать `ComponentFactory`, которая создаёт `AirdropComponent`. Можно наследовать `AbstractComponent`: реализовать `onCreate` и `onRemove`, публиковать плейсхолдеры через `exportPlaceholder`, входы через `exportEvent`. Тики доступны через `TimerSystem.Tickable`. За образец можно взять `TimerComponent` или `TimeoutComponent`.

Встроенный спавнер реализует `AirdropSpawner`: `create(Workflow)` возвращает `TickableEntity`, а `isGpsPublish()` сообщает о публикации в GPS. Глобальный спавнер реализует `GlobalSpawner`: получает `SpawnerContext` в `start(...)`, затем тикается менеджером. Для освобождения ресурсов глобального спавнера используйте lifecycle аддона; текущий интерфейс `GlobalSpawner` отдельного хука закрытия не содержит.

`NodeSchema` описывает поля, события, входы и плейсхолдеры для веб-редактора. Для компонентов схема обязательна. Генераторы и встроенные спавнеры технически можно зарегистрировать без неё, но в редакторе такой тип не появится. Глобальные спавнеры не имеют отдельного каталога схем в редакторе workflow.

## Контекст аддона и runtime API

`AbstractAddon` предоставляет `getPlugin()`, `getDescription()`, `getLogger()`, `getDataFolder()` и `getResource(name)`. Каталог данных — `addons/<имя>/`; создавайте его перед записью файлов. `getResource` читает ресурсы JAR самого аддона.

`BAF` даёт доступ к менеджеру событий, GPS, луту, реестру запусков, глобальным спавнерам и контекстам команд. Например, `BAF.launches().start("example:event")` запускает зарегистрированное событие. Список загруженных аддонов доступен через `getPlugin().addons().addons()`.

Регистрационные хуки выполняются синхронно. В runtime соблюдайте требования Bukkit к основному потоку; не меняйте компоненты события из асинхронных задач.
