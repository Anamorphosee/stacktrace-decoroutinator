package dev.reformator.stacktracedecoroutinator.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResultValueRetrieverImpl extends ResultValueRetriever {
    public static final ResultValueRetrieverImpl instance = new ResultValueRetrieverImpl();

    @Nullable
    @Override
    public Object retrieveResultValue(@NotNull Object result) {
        return result;
    }
}
