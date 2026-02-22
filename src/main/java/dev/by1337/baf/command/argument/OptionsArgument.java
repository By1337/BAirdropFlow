package dev.by1337.baf.command.argument;

import dev.by1337.baf.util.OptionsHolder;
import dev.by1337.cmd.*;

public class OptionsArgument<C> extends Argument<C, OptionsHolder> {
    public OptionsArgument(String name) {
        super(name);
    }

    public void parse(C ctx, CommandReader reader, ArgumentMap out) throws CommandMsgError {
        String src = reader.src();
        int idx = reader.ridx();
        if (idx < src.length()) {
            out.put(this.name, new OptionsHolder(src.substring(idx)));
            reader.ridx(src.length());
        }
    }

    public void suggest(C ctx, CommandReader reader, SuggestionsList suggestions, ArgumentMap args) throws CommandMsgError {
        reader.ridx(reader.length());
    }

    public boolean compilable() {
        return true;
    }

    public boolean allowAsync() {
        return true;
    }
}
