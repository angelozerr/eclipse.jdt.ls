package org.eclipse.jdt.ls.core.internal.services;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtension;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IRegistryEventListener;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethodProvider;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;

public class CustomServicesExtensionPoint implements IRegistryEventListener {

	private static CustomServicesExtensionPoint instance = null;

	private Map<String, JsonRpcMethod> supportedMethods;

	private CustomServiceEndpoint endpoint = new CustomServiceEndpoint();

	public static CustomServicesExtensionPoint getInstance() {
		if (instance == null) {
			instance = new CustomServicesExtensionPoint();
		}
		return instance;
	}

	/**
	 * @return the endpoint
	 */
	public Endpoint getEndpoint() {
		return endpoint;
	}

	/**
	 * Extension point ID for the delegate command handler.
	 */
	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.customServices";

	private static final String CLASS = "class";

	private static class DelegateCommandHandlerDescriptor {

		private final IConfigurationElement fConfigurationElement;

		private Set<String> fStaticCommandIds = new HashSet<>();;
		private Set<String> fNonStaticCommandIds = new HashSet<>();
		private Set<String> fAllCommands = new HashSet<>();

		private Object fDelegateCommandHandlerInstance;

		public DelegateCommandHandlerDescriptor(IConfigurationElement element) {
			fConfigurationElement = element;
		}

		public synchronized Object getDelegateCommandHandler() {
			if (fDelegateCommandHandlerInstance == null) {
				try {
					Object extension = fConfigurationElement.createExecutableExtension(CLASS);
					fDelegateCommandHandlerInstance = extension;
				} catch (CoreException e) {
					JavaLanguageServerPlugin.logException("Unable to create delegate command handler ", e);
					return null;
				}
			}
			return fDelegateCommandHandlerInstance;
		}

		public static String createId(IConfigurationElement element) {
			return element.getNamespaceIdentifier() + "#" + element.getAttribute(CLASS);
		}
	}

	private Map<String, DelegateCommandHandlerDescriptor> fgContributedCommandHandlers;

	private synchronized Collection<DelegateCommandHandlerDescriptor> getDelegateCommandHandlerDescriptors() {
		if (fgContributedCommandHandlers == null) {
			IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
			fgContributedCommandHandlers = Stream.of(elements).collect(Collectors.toMap(DelegateCommandHandlerDescriptor::createId, DelegateCommandHandlerDescriptor::new, (value1, value2) -> value2));

			Platform.getExtensionRegistry().addListener(this);
		}

		return fgContributedCommandHandlers.values();
	}

	@Override
	public synchronized void added(IExtension[] extensions) {
		Map<String, DelegateCommandHandlerDescriptor> addedDescriptors = Stream.of(extensions).filter(extension -> extension.getExtensionPointUniqueIdentifier().equals(EXTENSION_POINT_ID))
				.flatMap(extension -> Stream.of(extension.getConfigurationElements())).collect(Collectors.toMap(DelegateCommandHandlerDescriptor::createId, DelegateCommandHandlerDescriptor::new, (value1, value2) -> value2));

		fgContributedCommandHandlers.putAll(addedDescriptors);
	}

	@Override
	public synchronized void removed(IExtension[] extensions) {
		Stream.of(extensions).filter(extension -> extension.getExtensionPointUniqueIdentifier().equals(EXTENSION_POINT_ID)).flatMap(extension -> Stream.of(extension.getConfigurationElements()))
				.map(DelegateCommandHandlerDescriptor::createId).forEach(fgContributedCommandHandlers::remove);
	}

	@Override
	public void added(IExtensionPoint[] extensionPoints) {

	}

	@Override
	public void removed(IExtensionPoint[] extensionPoints) {

	}

	/**
	 * @param name
	 * @return
	 */
	public JsonRpcMethod getJsonRpcMethod(String name) {
		if (supportedMethods == null) {
			supportedMethods = getSupportedMethods();
		}
		return supportedMethods.get(name);
	}

	/**
	 * Gather all JSON-RPC methods from the local and remote services.
	 */
	protected Map<String, JsonRpcMethod> getSupportedMethods() {
		Map<String, JsonRpcMethod> supportedMethods = new LinkedHashMap<>();
		// Gather the supported methods of remote interfaces
		//		for (Class<?> interface_ : remoteInterfaces) {
		//			supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(interface_));
		//		}
		for (DelegateCommandHandlerDescriptor descriptor : getDelegateCommandHandlerDescriptors()) {
			Object localService = descriptor.getDelegateCommandHandler();
			if (localService instanceof JsonRpcMethodProvider) {
				JsonRpcMethodProvider rpcMethodProvider = (JsonRpcMethodProvider) localService;
				supportedMethods.putAll(rpcMethodProvider.supportedMethods());
			} else {
				supportedMethods.putAll(ServiceEndpoints.getSupportedMethods(localService.getClass()));
			}
			endpoint.addDelegate(localService);
		}
		return supportedMethods;
	}
}
