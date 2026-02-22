package dev.by1337.baf.ref;

import java.util.function.Consumer;

public class Resource<T> {
    private T t;
    private final Consumer<T> releaser;
    private final Cleaner cleaner;

    public Resource(T t, Consumer<T> releaser, AirdropCleaner airdropCleaner) {
        this.t = t;
        this.releaser = releaser;
        cleaner = airdropCleaner.addCleaner(this::clean);
    }

    public T get(){
        return t;
    }

    public void clean(){
        cleaner.cancel();
        if (t == null) return;
        releaser.accept(t);
    }
}
