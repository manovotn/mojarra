/*
 * Copyright (c) 2009, 2020 Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021 Contributors to Eclipse Foundation.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.faces.config;

import static com.sun.faces.RIConstants.ANNOTATED_CLASSES;
import static com.sun.faces.RIConstants.FACES_SERVLET_MAPPINGS;
import static com.sun.faces.RIConstants.FACES_SERVLET_REGISTRATION;
import static java.lang.Boolean.parseBoolean;
import static java.util.Collections.emptySet;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.Resource;
import jakarta.faces.annotation.FacesConfig;
import jakarta.faces.application.ResourceDependencies;
import jakarta.faces.application.ResourceDependency;
import jakarta.faces.component.FacesComponent;
import jakarta.faces.component.UIComponent;
import jakarta.faces.component.behavior.FacesBehavior;
import jakarta.faces.convert.Converter;
import jakarta.faces.convert.FacesConverter;
import jakarta.faces.event.ListenerFor;
import jakarta.faces.event.ListenersFor;
import jakarta.faces.event.NamedEvent;
import jakarta.faces.event.PhaseListener;
import jakarta.faces.flow.FlowScoped;
import jakarta.faces.flow.builder.FlowBuilderParameter;
import jakarta.faces.flow.builder.FlowDefinition;
import jakarta.faces.lifecycle.ClientWindowScoped;
import jakarta.faces.model.DataModel;
import jakarta.faces.model.FacesDataModel;
import jakarta.faces.push.Push;
import jakarta.faces.push.PushContext;
import jakarta.faces.render.FacesBehaviorRenderer;
import jakarta.faces.render.FacesRenderer;
import jakarta.faces.render.Renderer;
import jakarta.faces.validator.FacesValidator;
import jakarta.faces.validator.Validator;
import jakarta.faces.view.ViewScoped;
import jakarta.faces.webapp.FacesServlet;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import jakarta.servlet.annotation.HandlesTypes;
import jakarta.websocket.server.ServerContainer;

/**
 * Initializes Jakarta Faces if at least one of the following conditions is met:
 *
 * <ul>
 * <li>The <code>Set</code> of classes passed to this initializer contains an user-defined Faces type, or</li>
 * <li><code>FacesServlet</code> has been explicitly mapped ,or</li>
 * <li><code>/WEB-INF/faces-config.xml</code> exists</li>
 * </ul>
 *
 * If it is met, and the <code>FacesServlet</code> has not been explicitly mapped,
 * then add mappings <em>*.xhtml</em>, <em>/faces</em>, <em>*.jsf</em>, and <em>*.faces</em> for the FacesServlet.
 */
@HandlesTypes({

    // Jakarta Faces specific
    ClientWindowScoped.class, Converter.class, DataModel.class, FacesBehavior.class, FacesBehaviorRenderer.class, FacesComponent.class, FacesConfig.class,
    FacesConverter.class, FacesDataModel.class, FacesRenderer.class, FacesValidator.class, FlowBuilderParameter.class, FlowDefinition.class, FlowScoped.class,
    ListenerFor.class, ListenersFor.class, NamedEvent.class, PhaseListener.class, Push.class, Renderer.class, ResourceDependencies.class, ResourceDependency.class,
    UIComponent.class, Validator.class, ViewScoped.class,

    // General -- TODO: document why exactly this is relevant; it was added here https://github.com/javaee/mojarra/commit/74cb6a1c35d74d1a3097b8c02d746bda42a40507
    Resource.class,
})
public class FacesInitializer implements ServletContainerInitializer {

    // NOTE: Logging should not be used with this class.

    public static final String FACES_PACKAGE_PREFIX = "jakarta.faces.";
    public static final String MOJARRA_PACKAGE_PREFIX = "com.sun.faces.";
    public static final String MOJARRA_TEST_PACKAGE_PREFIX = "com.sun.faces.test.";

    private static final String FACES_CONFIG_RESOURCE_PATH = "/WEB-INF/faces-config.xml";
    private static final String FACES_SERVLET_CLASS_NAME = FacesServlet.class.getName();
    private static final String[] FACES_SERVLET_MAPPINGS_WITH_XHTML = { "/faces/*", "*.jsf", "*.faces", "*.xhtml" };
    private static final String[] FACES_SERVLET_MAPPINGS_WITHOUT_XHTML = { "/faces/*", "*.jsf", "*.faces" };

    // -------------------------------- Methods from ServletContainerInitializer

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {
        Set<Class<?>> customFacesTypes = filterCustomFacesTypes(classes);

        boolean appHasFacesContent = !customFacesTypes.isEmpty() || isFacesConfigFilePresent(servletContext);
        boolean appHasFacesServlet = isFacesServletRegistrationPresent(servletContext);

        if (appHasFacesContent || appHasFacesServlet) {
            servletContext.setAttribute(ANNOTATED_CLASSES, customFacesTypes);
            InitFacesContext initFacesContext = new InitFacesContext(servletContext);

            try {
                if (appHasFacesContent) {
                    // Only look at mapping concerns if there is Faces content
                    handleMappingConcerns(servletContext);
                }

                // Other concerns also handled if there is an existing Faces Servlet mapping
                handleWebSocketConcerns(servletContext);

                // The Configure listener will do the bulk of initializing (configuring) Faces in a later phase.
                servletContext.addListener(ConfigureListener.class);
            }
            finally {
                // Bug 20458755: The InitFacesContext was not being cleaned up, resulting in
                // a partially constructed FacesContext being made available
                // to other code that re-uses this Thread at init time.
                initFacesContext.releaseCurrentInstance();
                initFacesContext.release();
            }
        }
    }

    // --------------------------------------------------------- Private Methods

    private static Set<Class<?>> filterCustomFacesTypes(Set<Class<?>> classes) {
        Set<Class<?>> set = new HashSet<>(classes == null ? emptySet() : classes);
        set.removeIf(FacesInitializer::isApiOrImplClass);
        return set;
    }

    private static boolean isApiOrImplClass(Class<?> c) {
        String name = c.getName();
        return name.startsWith(FACES_PACKAGE_PREFIX) || (name.startsWith(MOJARRA_PACKAGE_PREFIX) && !name.startsWith(MOJARRA_TEST_PACKAGE_PREFIX));
    }

    private static boolean isFacesConfigFilePresent(ServletContext context) {
        try {
            return context.getResource(FACES_CONFIG_RESOURCE_PATH) != null;
        }
        catch (Exception ignore) {
            return false;
        }
    }

    private static boolean isFacesServletRegistrationPresent(ServletContext context) {
        return getExistingFacesServletRegistration(context) != null;
    }

    private static ServletRegistration getExistingFacesServletRegistration(ServletContext servletContext) {
        Map<String, ? extends ServletRegistration> existing = servletContext.getServletRegistrations();

        for (ServletRegistration registration : existing.values()) {
            if (FACES_SERVLET_CLASS_NAME.equals(registration.getClassName())) {
                return registration;
            }
        }

        return null;
    }

    private static void handleMappingConcerns(ServletContext servletContext) throws ServletException {
        ServletRegistration existingFacesServletRegistration = getExistingFacesServletRegistration(servletContext);

        if (existingFacesServletRegistration != null) {
            // FacesServlet has already been defined, so we're not going to add additional mappings.
            servletContext.setAttribute(FACES_SERVLET_REGISTRATION, existingFacesServletRegistration);
            return;
        }

        ServletRegistration newFacesServletRegistration = servletContext.addServlet(FacesServlet.class.getSimpleName(), FACES_SERVLET_CLASS_NAME);

        if (parseBoolean(servletContext.getInitParameter(FacesServlet.DISABLE_FACESSERVLET_TO_XHTML_PARAM_NAME))) {
            newFacesServletRegistration.addMapping(FACES_SERVLET_MAPPINGS_WITHOUT_XHTML);
        }
        else {
            newFacesServletRegistration.addMapping(FACES_SERVLET_MAPPINGS_WITH_XHTML);
        }

        servletContext.setAttribute(FACES_SERVLET_MAPPINGS, newFacesServletRegistration.getMappings());
        servletContext.setAttribute(FACES_SERVLET_REGISTRATION, newFacesServletRegistration);
    }

    private static void handleWebSocketConcerns(ServletContext context) throws ServletException {
        if (context.getAttribute(ServerContainer.class.getName()) != null) {
            // Already initialized
            return;
        }

        if (!parseBoolean(context.getInitParameter(PushContext.ENABLE_WEBSOCKET_ENDPOINT_PARAM_NAME))) {
            // Register websocket endpoint is not enabled
            return;
        }

        // Below work around is specific for Tyrus websocket impl.
        // As Mojarra is to be designed as a container-provided JAR (not an user-provided JAR),
        // the WebsocketEndpoint needs to be programmatically added in a ServletContextListener (in Mojarra's case the ConfigureListener),
        // but at that point, servletContext.getAttribute(ServerContainer.class.getName()) returns null when Tyrus is used (Payara/GlassFish).
        // Upon inspection it turns out that the TyrusServletContainerInitializer#onStartup() immediately returns when there are no user-provided endpoints
        // and doesn't register the ServerContainer anymore, causing it to not be placed in ServletContext anymore.
        // The below work around will thus manually take care of this.

        ClassLoader cl = context.getClassLoader();
        Class<?> tyrusInitializerClass;

        try {
            tyrusInitializerClass = cl.loadClass("org.glassfish.tyrus.servlet.TyrusServletContainerInitializer");
        }
        catch (ClassNotFoundException ignore) {
            // Tyrus is actually not being used (all other impls known so far do not skip ServerContainer). That's OK for now, so just continue as usual.
            return;
        }

        try {
            ServletContainerInitializer tyrusInitializer = (ServletContainerInitializer) tyrusInitializerClass.getDeclaredConstructor().newInstance();
            Class<?> tyrusConfigClass = cl.loadClass("org.glassfish.tyrus.server.TyrusServerConfiguration");

            // Set of classes must be mutable and contain the TyrusServerConfiguration class.
            Set<Class<?>> handledTypes = new HashSet<>();
            handledTypes.add(tyrusConfigClass);
            tyrusInitializer.onStartup(handledTypes, context);
        }
        catch (Exception e) {
            // Tyrus is being used but it failed for unclear reason while websocket endpoint should be enabled. That's not OK, so rethrow as ServletException.
            throw new ServletException(e);
        }

    }

}
