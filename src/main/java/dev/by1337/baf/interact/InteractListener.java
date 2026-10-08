package dev.by1337.baf.interact;

import org.bukkit.event.Event;

public interface InteractListener<T extends Event> {
    void on(T t);

    void unregister();

}
