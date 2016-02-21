package org.neo4j.index.memory;

import org.neo4j.helpers.Service;
import org.neo4j.kernel.api.index.SchemaIndexProvider;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.extension.KernelExtensionFactory;
import org.neo4j.kernel.impl.spi.KernelContext;
import org.neo4j.kernel.lifecycle.Lifecycle;

@Service.Implementation(KernelExtensionFactory.class)
public class MemoryIndexProviderFactory extends KernelExtensionFactory<MemoryIndexProviderFactory.Dependencies> {
    public static final String KEY = "memory-index";

    public static final SchemaIndexProvider.Descriptor PROVIDER_DESCRIPTOR =
            new SchemaIndexProvider.Descriptor(KEY, "1.0");

    private final MemorySchemaIndexProvider singleProvider;

    public interface Dependencies {
        Config getConfig();
    }

    public MemoryIndexProviderFactory() {
        this(null);
    }

    public MemoryIndexProviderFactory(MemorySchemaIndexProvider singleProvider) {
        super(KEY);
        this.singleProvider = singleProvider;
    }

    @Override
    public Lifecycle newInstance(KernelContext context, Dependencies dependencies) throws Throwable {
        return singleProvider != null ? singleProvider : new MemorySchemaIndexProvider(dependencies.getConfig());
    }
}
