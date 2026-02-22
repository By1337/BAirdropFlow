package dev.by1337.baf.ref;

import java.util.function.Consumer;

public class CleanerSlot<T> {
    private T value;
    private final Consumer<T> releaser;
    private final AirdropCleaner airdropCleaner;
    private Cleaner cleaner;

    public CleanerSlot(Consumer<T> releaser, AirdropCleaner airdropCleaner) {
        this.releaser = releaser;
        this.airdropCleaner = airdropCleaner;
    }

    public T get() {
        return value;
    }

    public void set(T value) {
        if (this.value != null) {
            cleanup();
        }

        this.value = value;
        cleaner = (value != null)
                ? airdropCleaner.addCleaner(this::cleanup)
                : null;
    }

    public void cleanup() {
        Cleaner c = cleaner;
        cleaner = null;

        if (c != null) c.cancel();

        T v = value;
        value = null;

        if (v != null) {
            releaser.accept(v);
        }
    }
}
