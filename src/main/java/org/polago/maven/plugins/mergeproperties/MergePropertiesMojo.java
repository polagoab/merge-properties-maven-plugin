/*
 * Copyright 1014-2016 Polago AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.polago.maven.plugins.mergeproperties;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;

/**
 * Merges a set of properties files into an output file.
 */
@Mojo(name = "merge", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresProject = true, threadSafe = true)
public class MergePropertiesMojo extends AbstractMojo implements Contextualizable {

    /**
     * The Maven Project to use.
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    /**
     * The MavenSession instance to use.
     */
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    /**
     * The MavenResourcesFiltering instance to use.
     */
    @Component(role = MavenResourcesFiltering.class, hint = "merge")
    private MergeProperitesMavenResourcesFiltering mavenResourcesFiltering;

    /**
     * The PlexusContainer instance to use.
     */
    private PlexusContainer plexusContainer;

    /**
     * The output directory into which to create the outputFile.
     */
    @Parameter(required = true)
    private File outputDirectory;

    /**
     * The output filename that the properties should be merged into relative to the outputDirectory.
     */
    @Parameter(required = true)
    private String outputFile;

    /**
     * The list of resources to merge. Please see the <a href="http://maven.apache.org/pom.html#Resources">POM
     * Reference</a> for a description of how to specify the resources element. Note that the
     * <code>&lt;targetPath&gt;</code> element is always ignored and the default <code>&lt;include&gt;</code> pattern is
     * <code>**&#47;*.properties</code>.
     */
    @Parameter(required = true)
    private List<Resource> resources;

    /**
     * The character encoding scheme to use. Note that Java Properties files are always encoded in ISO-8859-1.
     */
    @Parameter(property = "encoding", defaultValue = "ISO-8859-1")
    private String encoding;

    /**
     * The list of additional filter properties files to be used along with System and project properties.
     * <p>
     * See also: {@link #filters}.
     */
    @Parameter(defaultValue = "${project.build.filters}", readonly = true)
    private List<String> buildFilters;

    /**
     * The list of extra filter properties files to be used along with System properties, project properties, and filter
     * properties files specified in the POM build/filters section, which should be used for the filtering during the
     * current mojo execution.
     */
    @Parameter
    private List<String> filters;

    /**
     * If false, don't use the filters specified in the build/filters section of the POM when processing resources in
     * this mojo execution.
     * <p>
     * See also: {@link #buildFilters} and {@link #filters}
     */
    @Parameter(defaultValue = "true")
    private boolean useBuildFilters;

    /**
     * Expression preceded with this String won't be interpolated \${foo} will be replaced with ${foo}.
     */
    @Parameter(property = "maven.resources.escapeString")
    private String escapeString;

    /**
     * Overwrite any existing outputFile even if the outputFile file is newer.
     */
    @Parameter(property = "maven.resources.overwrite", defaultValue = "false")
    private boolean overwrite;

    /**
     * Overwrite any duplicate properties instead of failing the build.
     */
    @Parameter(property = "maven.resources.overwrite", defaultValue = "false")
    private boolean overwriteProperties;

    /**
     * Whether to escape backslashes and colons in windows-style paths.
     */
    @Parameter(property = "maven.resources.escapeWindowsPaths", defaultValue = "true")
    private boolean escapeWindowsPaths;

    /**
     * <p>
     * Set of delimiters for expressions to filter within the resources. These delimiters are specified in the form
     * 'beginToken*endToken'. If no '*' is given, the delimiter is assumed to be the same for start and end.
     * </p>
     * <p>
     * So, the default filtering delimiters might be specified as:
     * </p>
     *
     * <pre>
     * &lt;delimiters&gt;
     *   &lt;delimiter&gt;${*}&lt;/delimiter&gt;
     *   &lt;delimiter&gt;@&lt;/delimiter&gt;
     * &lt;/delimiters&gt;
     * </pre>
     * <p>
     * Since the '@' delimiter is the same on both ends, we don't need to specify '@*@' (though we can).
     * </p>
     */
    @Parameter
    private LinkedHashSet<String> delimiters;

    /**
     * If false, don't use the maven's built-in delimiters.
     */
    @Parameter(defaultValue = "true")
    private boolean useDefaultDelimiters;

    /**
     * <p>
     * List of plexus components hints which implements
     * {@link MavenResourcesFiltering#filterResources(MavenResourcesExecution)} . They will be executed after the
     * resources copying/filtering.
     * </p>
     */
    @Parameter
    private List<String> mavenFilteringHints;

    /**
     * The list of user filtering components to use.
     */
    private final List<MavenResourcesFiltering> mavenFilteringComponents = new ArrayList<MavenResourcesFiltering>();

    /**
     * Stop searching endToken at the end of line.
     */
    @Parameter(property = "maven.resources.supportMultiLineFiltering", defaultValue = "false")
    private boolean supportMultiLineFiltering;

    /**
     * Skip the execution of the plugin if you need to.
     *
     * @since 1.1
     */
    @Parameter(property = "maven.resources.skip", defaultValue = "false")
    private boolean skip;

    /**
     * {@inheritDoc}
     */
    @Override
    public void contextualize(Context context) throws ContextException {
        plexusContainer = (PlexusContainer) context.get(PlexusConstants.PLEXUS_KEY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException {
        if (isSkip()) {
            getLog().info("Skipping the execution.");
            return;
        }

        mavenResourcesFiltering.setOutputFile(outputFile);
        mavenResourcesFiltering.setOverwriteProperties(overwriteProperties);

        try {

            if (StringUtils.isEmpty(encoding) && isFilteringEnabled(getResources())) {
                getLog().warn("File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING
                    + ", i.e. build is platform dependent!");
            }

            List<String> combinedFilters = getCombinedFiltersList();

            MavenResourcesExecution mavenResourcesExecution = new MavenResourcesExecution(getResources(),
                getOutputDirectory(), project, encoding, combinedFilters, Collections.<String> emptyList(), session);

            mavenResourcesExecution.setEscapeWindowsPaths(escapeWindowsPaths);

            // never include project build filters in this call, since we've
            // already accounted for the POM build filters
            // above, in getCombinedFiltersList().
            mavenResourcesExecution.setInjectProjectBuildFilters(false);

            mavenResourcesExecution.setEscapeString(escapeString);
            mavenResourcesExecution.setOverwrite(overwrite);
            mavenResourcesExecution.setIncludeEmptyDirs(false);
            mavenResourcesExecution.setSupportMultiLineFiltering(supportMultiLineFiltering);

            // Handle subject of MRESOURCES-99
            Properties additionalProperties = addSeveralSpecialProperties();
            mavenResourcesExecution.setAdditionalProperties(additionalProperties);

            // if these are NOT set, just use the defaults, which are '${*}' and '@'.
            mavenResourcesExecution.setDelimiters(delimiters, useDefaultDelimiters);

            mavenResourcesFiltering.filterResources(mavenResourcesExecution);

            executeUserFilterComponents(mavenResourcesExecution);
        } catch (MavenFilteringException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * This solves https://issues.apache.org/jira/browse/MRESOURCES-99.<br/>
     * BUT:<br/>
     * This should be done different than defining those properties a second time, cause they have already being defined
     * in Maven Model Builder (package org.apache.maven.model.interpolation) via BuildTimestampValueSource. But those
     * can't be found in the context which can be got from the maven core.<br/>
     * A solution could be to put those values into the context by Maven core so they are accessible everywhere. (I'm
     * not sure if this is a good idea). Better ideas are always welcome. The problem at the moment is that maven core
     * handles usage of properties and replacements in the model, but does not the resource filtering which needed some
     * of the properties.
     *
     * @return the new instance with those properties.
     */
    private Properties addSeveralSpecialProperties() {
        String timeStamp = new MavenBuildTimestamp().formattedTimestamp();
        Properties additionalProperties = new Properties();
        additionalProperties.put("maven.build.timestamp", timeStamp);
        if (project.getBasedir() != null) {
            additionalProperties.put("project.baseUri", project.getBasedir().getAbsoluteFile().toURI().toString());
        }

        return additionalProperties;
    }

    /**
     * Execute any user filters.
     *
     * @param mavenResourcesExecution the MavenResourcesExecution to use
     * @throws MojoExecutionException indicating build error
     * @throws MavenFilteringException indicating filtering problem
     */
    private void executeUserFilterComponents(MavenResourcesExecution mavenResourcesExecution)
        throws MojoExecutionException, MavenFilteringException {

        if (mavenFilteringHints != null) {
            for (String hint : mavenFilteringHints) {
                try {
                    mavenFilteringComponents.add((MavenResourcesFiltering) plexusContainer
                        .lookup(MavenResourcesFiltering.class.getName(), hint));
                } catch (ComponentLookupException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }
            }
        } else {
            getLog().debug("no user filter components");
        }

        if (mavenFilteringComponents != null && !mavenFilteringComponents.isEmpty()) {
            getLog().debug("execute user filters");
            for (MavenResourcesFiltering filter : mavenFilteringComponents) {
                filter.filterResources(mavenResourcesExecution);
            }
        }
    }

    /**
     * Gets the combined list of all filters to use.
     *
     * @return a List of all filters to use
     */
    private List<String> getCombinedFiltersList() {
        if (filters == null || filters.isEmpty()) {
            return useBuildFilters ? buildFilters : null;
        } else {
            List<String> result = new ArrayList<String>();

            if (useBuildFilters && buildFilters != null && !buildFilters.isEmpty()) {
                result.addAll(buildFilters);
            }

            result.addAll(filters);

            return result;
        }
    }

    /**
     * Determines whether filtering has been enabled for any resource.
     *
     * @param targets The set of resources to check for filtering, may be <code>null</code>.
     * @return <code>true</code> if at least one resource uses filtering, <code>false</code> otherwise.
     */
    private boolean isFilteringEnabled(Collection<Resource> targets) {
        if (targets != null) {
            for (Resource resource : targets) {
                if (resource.isFiltering()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the Resources to use.
     *
     * @return a list of Resources
     */
    public List<Resource> getResources() {
        return resources;
    }

    /**
     * Gets the outputDirectory to use.
     *
     * @return the outputDirectory for this Mojo
     */
    public File getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Gets the mavenResourcesFiltering property value.
     *
     * @return the current value of the mavenResourcesFiltering property
     */
    public MergeProperitesMavenResourcesFiltering getMavenResourcesFiltering() {
        return mavenResourcesFiltering;
    }

    /**
     * Sets the mavenResourcesFiltering property.
     *
     * @param mavenResourcesFiltering the new property value
     */
    public void setMavenResourcesFiltering(MergeProperitesMavenResourcesFiltering mavenResourcesFiltering) {
        this.mavenResourcesFiltering = mavenResourcesFiltering;
    }

    /**
     * Sets the project property.
     *
     * @param project the new property value
     */
    public void setProject(MavenProject project) {
        this.project = project;
    }

    /**
     * Sets the overwriteProperties property.
     *
     * @param overwriteProperties the new property value
     */
    public void setOverwriteProperties(boolean overwriteProperties) {
        this.overwriteProperties = overwriteProperties;
    }

    /**
     * Gets the skip property value.
     *
     * @return the current value of the skip property
     */
    public boolean isSkip() {
        return skip;
    }

}
