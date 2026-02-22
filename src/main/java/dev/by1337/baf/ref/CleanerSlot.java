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
        if (value != null){
            cleanup();
        }
        this.value = value;
        cleaner = airdropCleaner.addCleaner(this::cleanup);
    }

    public void cleanup() {
        if (cleaner != null) cleaner.cancel();
        if (value == null) return;
        releaser.accept(value);
        value = null;
        cleaner = null;
    }
}
