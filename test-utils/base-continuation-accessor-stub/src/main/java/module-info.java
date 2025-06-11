import dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub.TestBaseContinuationAccessorProviderAccessor;
import dev.reformator.stacktracedecoroutinator.provider.internal.BaseContinuationAccessorProvider;

module dev.reformator.stacktracedecoroutinator.test.basecontinuationaccessorstub {
    requires kotlin.stdlib;
    requires static intrinsics;
    requires dev.reformator.stacktracedecoroutinator.provider;

    provides BaseContinuationAccessorProvider with TestBaseContinuationAccessorProviderAccessor;
}