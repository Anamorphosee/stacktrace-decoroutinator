import dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub.TestBaseContinuationAccessorProviderAccessor;
import dev.reformator.stacktracedecoroutinator.provider.DecoroutinatorBaseContinuationAccessorProvider;

module dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub {
    requires kotlin.stdlib;
    requires static intrinsics;
    requires dev.reformator.stacktracedecoroutinator.provider;

    provides DecoroutinatorBaseContinuationAccessorProvider with TestBaseContinuationAccessorProviderAccessor;
}