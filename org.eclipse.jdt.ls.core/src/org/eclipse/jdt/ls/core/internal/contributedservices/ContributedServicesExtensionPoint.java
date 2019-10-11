/*******************************************************************************
 * Copyright (c) 2019 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.contributedservices;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.IConstants;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;

/**
 * @author azerr
 *
 */
public class ContributedServicesExtensionPoint extends ContributedServicesRegistry {

	private static ContributedServicesExtensionPoint INSTANCE;

	/**
	 * Extension point ID for the external services.
	 */
	private static final String EXTENSION_POINT_ID = "org.eclipse.jdt.ls.core.contributedServices";

	private static final String CLASS = "class";

	private static final String REQUEST = "request";

	private static final String REQUEST_ID = "id";

	/**
	 * @return the iNSTANCE
	 */
	public static ContributedServicesExtensionPoint getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new ContributedServicesExtensionPoint();
		}
		return INSTANCE;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.externalservices.ExternalServicesRegistry#init()
	 */
	@Override
	protected void init() {
		IConfigurationElement[] elements = Platform.getExtensionRegistry().getConfigurationElementsFor(EXTENSION_POINT_ID);
		for (IConfigurationElement element : elements) {
			registerExternalService(element);
		}
	}

	/**
	 * @param externalService
	 */
	private void registerExternalService(IConfigurationElement externalService) {
		ContributedServiceWrapper wrapper = new ContributedServiceWrapper() {

			@Override
			public Object getLocalService() {
				try {
					return externalService.createExecutableExtension(CLASS);
				} catch (CoreException e) {
					IStatus status = new Status(IStatus.ERROR, IConstants.PLUGIN_ID, IStatus.OK, "Error in creating local service in external services", e);
					JavaLanguageServerPlugin.log(status);
					return null;
				}
			}
		};

		IConfigurationElement[] requests = externalService.getChildren(REQUEST);
		for (IConfigurationElement request : requests) {
			String name = request.getAttribute(REQUEST_ID);
			super.register(name, wrapper);
		}
	}

}
