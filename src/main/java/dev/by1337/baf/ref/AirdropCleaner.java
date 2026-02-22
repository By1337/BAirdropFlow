package dev.by1337.baf.ref;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public class AirdropCleaner {
    private static final Logger log = LoggerFactory.getLogger(AirdropCleaner.class);
    private Set<Cleaner> cleaners = Collections.newSetFromMap(new IdentityHashMap<>());

    public Cleaner addCleaner(Runnable r) {
        if (cleaners == null) {
            throw new IllegalStateException("cleaner is closed!");
        }
        var v = new Cleaner() {

            @Override
            public void run() {
                r.run();
            }

            @Override
            public void cancel() {
                if (cleaners != null)
                    cleaners.remove(this);
            }
        };
        cleaners.add(v);
        return v;
    }

    public void cleanup() {
        var set = cleaners;
        cleaners = null;
        for (Cleaner cleaner : set) {
            try {
                cleaner.run();
            }catch (Exception e){
                log.error("Failed to run cleaner", e);
            }
        }
    }
}
