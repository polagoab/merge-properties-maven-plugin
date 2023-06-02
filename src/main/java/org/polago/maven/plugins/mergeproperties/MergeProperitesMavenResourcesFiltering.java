/*
 * Copyright 2014-2023 Polago AB.
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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.MapConfiguration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.PropertiesConfigurationLayout;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.io.FilenameUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.shared.filtering.FilterWrapper;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.maven.shared.utils.PathTool;
import org.apache.maven.shared.utils.ReaderFactory;
import org.apache.maven.shared.utils.StringUtils;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Initializable;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.Scanner;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * MavenResourcesFiltering Plexus Component that merges properties into a single file.
 */
@Named("merge")
@Singleton
public class MergeProperitesMavenResourcesFiltering extends AbstractLogEnabled
    implements MavenResourcesFiltering, Initializable {

    private static final String[] EMPTY_STRING_ARRAY = {};

    private static final String[] DEFAULT_INCLUDES = {"**/**.properties"};

    private List<String> defaultNonFilteredFileExtensions;

    private final MavenFileFilter mavenFileFilter;

    private BuildContext buildContext;

    private String outputFile;

    private boolean overwriteProperties = false;

    /**
     * Public Constructor.
     *
     * @param mavenFileFilter the {@link MavenFileFilter} to use
     * @param buildContext the {@link BuildContext} to use
     */
    @Inject
    public MergeProperitesMavenResourcesFiltering(MavenFileFilter mavenFileFilter, BuildContext buildContext) {
        super();
        this.mavenFileFilter = mavenFileFilter;
        this.buildContext = buildContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() throws InitializationException {

        this.defaultNonFilteredFileExtensions = new ArrayList<String>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean filteredFileExtension(String fileName, List<String> userNonFilteredFileExtensions) {
        List<String> nonFilteredFileExtensions = new ArrayList<String>(getDefaultNonFilteredFileExtensions());
        if (userNonFilteredFileExtensions != null) {
            nonFilteredFileExtensions.addAll(userNonFilteredFileExtensions);
        }
        boolean filteredFileExtension =
            !nonFilteredFileExtensions.contains(StringUtils.lowerCase(FilenameUtils.getExtension(fileName)));
        if (getLogger().isDebugEnabled()) {
            getLogger().debug(
                "file " + fileName + " has a" + (filteredFileExtension ? " " : " non ") + "filtered file extension");
        }
        return filteredFileExtension;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getDefaultNonFilteredFileExtensions() {
        if (this.defaultNonFilteredFileExtensions == null) {
            this.defaultNonFilteredFileExtensions = new ArrayList<String>();
        }
        return this.defaultNonFilteredFileExtensions;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void filterResources(MavenResourcesExecution mavenResourcesExecution) throws MavenFilteringException {

        if (mavenResourcesExecution == null) {
            throw new MavenFilteringException("mavenResourcesExecution cannot be null");
        }

        if (mavenResourcesExecution.getResources() == null) {
            getLogger().info("No resources configured, skipping merging");
            return;
        }

        if (mavenResourcesExecution.getOutputDirectory() == null) {
            throw new MavenFilteringException("outputDirectory cannot be null");
        }

        if (mavenResourcesExecution.isUseDefaultFilterWrappers()) {
            handleDefaultFilterWrappers(mavenResourcesExecution);
        }

        if (mavenResourcesExecution.getEncoding() == null || mavenResourcesExecution.getEncoding().length() < 1) {
            getLogger().warn("Using platform encoding (" + ReaderFactory.FILE_ENCODING
                + " actually) to merge properties, i.e. build is platform dependent!");
        } else {
            getLogger().info("Using '" + mavenResourcesExecution.getEncoding() + "' encoding to merge properties.");
        }

        Properties outputProperties = new Properties();
        long lastModified = 0L;

        for (Resource resource : mavenResourcesExecution.getResources()) {

            if (getLogger().isDebugEnabled()) {
                String ls = System.getProperty("line.separator");
                StringBuffer debugMessage =
                    new StringBuffer("Resource with targetPath " + resource.getTargetPath()).append(ls);
                debugMessage.append("directory " + resource.getDirectory()).append(ls);
                debugMessage
                    .append(
                        "excludes " + (resource.getExcludes() == null ? " empty " : resource.getExcludes().toString()))
                    .append(ls);
                debugMessage.append(
                    "includes " + (resource.getIncludes() == null ? " empty " : resource.getIncludes().toString()));
                getLogger().debug(debugMessage.toString());
            }

            String targetPath = resource.getTargetPath();

            File resourceDirectory = new File(resource.getDirectory());

            if (!resourceDirectory.isAbsolute()) {
                resourceDirectory =
                    new File(mavenResourcesExecution.getResourcesBaseDirectory(), resourceDirectory.getPath());
            }

            if (!resourceDirectory.exists()) {
                getLogger().info("Skipping non-existing resourceDirectory: " + resourceDirectory.getPath());
                continue;
            }

            // this part is required in case the user specified "../something" as destination. See MNG-1345.
            File outputDirectory = mavenResourcesExecution.getOutputDirectory();
            boolean outputExists = outputDirectory.exists();
            if (!outputExists && !outputDirectory.mkdirs()) {
                throw new MavenFilteringException("Cannot create resource output directory: " + outputDirectory);
            }

            boolean ignoreDelta = !outputExists || mavenResourcesExecution.isOverwrite()
                || buildContext.hasDelta(mavenResourcesExecution.getFileFilters())
                || buildContext.hasDelta(getRelativeOutputDirectory(mavenResourcesExecution));
            getLogger().debug("ignoreDelta " + ignoreDelta);
            Scanner scanner = buildContext.newScanner(resourceDirectory, ignoreDelta);

            setupScanner(resource, scanner, mavenResourcesExecution.isAddDefaultExcludes());

            scanner.scan();

            List<String> includedFiles = Arrays.asList(scanner.getIncludedFiles());
            if (!ignoreDelta && buildContext.isIncremental() && !includedFiles.isEmpty()) {
                // Perform a full scan since we need to consider all files when the file list is nonEmpty in
                // an incremental build
                getLogger().debug("Reverting to full scan");

                scanner = buildContext.newScanner(resourceDirectory, true);
                setupScanner(resource, scanner, mavenResourcesExecution.isAddDefaultExcludes());
                scanner.scan();
                includedFiles = Arrays.asList(scanner.getIncludedFiles());
            }

            getLogger().info("Merging " + includedFiles.size() + " resource" + (includedFiles.size() > 1 ? "s" : "")
                + (targetPath == null ? "" : " to " + targetPath));

            for (String name : includedFiles) {
                getLogger().debug("Processing file " + name);
                File source = new File(resourceDirectory, name);
                lastModified = Math.max(lastModified, source.lastModified());

                boolean filteredExt =
                    filteredFileExtension(source.getName(), mavenResourcesExecution.getNonFilteredFileExtensions());

                mergeProperties(outputProperties, source, resource.isFiltering() && filteredExt,
                    mavenResourcesExecution.getFilterWrappers(), mavenResourcesExecution.getEncoding(),
                    overwriteProperties);
            }

        }

        File destinationFile = getDestinationFile(mavenResourcesExecution.getOutputDirectory(), outputFile);
        if (mavenResourcesExecution.isOverwrite() || lastModified > destinationFile.lastModified()) {
            storeProperties(outputProperties, destinationFile);
        } else {
            getLogger().info("Skipping merge since no files were modified");
        }
    }

    /**
     * Add Default Filter Wrappers to the MavenResourcesExecution instance.
     *
     * @param mavenResourcesExecution the instance to modify
     * @throws MavenFilteringException indicating error
     */
    private void handleDefaultFilterWrappers(MavenResourcesExecution mavenResourcesExecution)
        throws MavenFilteringException {
        List<FilterWrapper> filterWrappers = new ArrayList<>();
        if (mavenResourcesExecution.getFilterWrappers() != null) {
            filterWrappers.addAll(mavenResourcesExecution.getFilterWrappers());
        }
        filterWrappers.addAll(mavenFileFilter.getDefaultFilterWrappers(mavenResourcesExecution));
        mavenResourcesExecution.setFilterWrappers(filterWrappers);
    }

    /**
     * Gets the destination file for the given file and dir.
     *
     * @param outputDirectory the directory used when file is a relative path
     * @param file the file path
     * @return a File representing the file
     */
    private File getDestinationFile(File outputDirectory, String file) {
        File destinationFile = new File(file);
        if (!destinationFile.isAbsolute()) {
            destinationFile = new File(outputDirectory, file);
        }

        if (!destinationFile.getParentFile().exists()) {
            destinationFile.getParentFile().mkdirs();
        }
        return destinationFile;
    }

    /**
     * Prepare the Scanner for use.
     *
     * @param resource the Resource to process
     * @param scanner the Scanner to setup
     * @param addDefaultExcludes if true, add default excludes to the Scanner
     */
    private void setupScanner(Resource resource, Scanner scanner, boolean addDefaultExcludes) {
        String[] includes;
        if (resource.getIncludes() != null && !resource.getIncludes().isEmpty()) {
            includes = resource.getIncludes().toArray(EMPTY_STRING_ARRAY);
        } else {
            includes = DEFAULT_INCLUDES;
        }
        scanner.setIncludes(includes);

        String[] excludes;
        if (resource.getExcludes() != null && !resource.getExcludes().isEmpty()) {
            excludes = resource.getExcludes().toArray(EMPTY_STRING_ARRAY);
            scanner.setExcludes(excludes);
        }

        if (addDefaultExcludes) {
            scanner.addDefaultExcludes();
        }

        scanner.addDefaultExcludes();
    }

    /**
     * Gets the relative path based on the project basedir.
     *
     * @param execution the MavenResourcesExecution instance to use
     * @return a path relative to the projects base directory
     */
    private String getRelativeOutputDirectory(MavenResourcesExecution execution) {
        String relOutDir = execution.getOutputDirectory().getAbsolutePath();

        if (execution.getMavenProject() != null && execution.getMavenProject().getBasedir() != null) {
            String basedir = execution.getMavenProject().getBasedir().getAbsolutePath();
            relOutDir = PathTool.getRelativeFilePath(basedir, relOutDir);
            if (relOutDir == null) {
                relOutDir = execution.getOutputDirectory().getPath();
            } else {
                relOutDir = relOutDir.replace('\\', '/');
            }
        }

        return relOutDir;
    }

    /**
     * Write the Properties to the given file using apache Commons-Configuration to avoid timestamp header.
     *
     * @param properties the Properties to use
     * @param file the file to store Properties into
     * @throws MavenFilteringException indicating File IO Error
     */
    protected void storeProperties(Properties properties, File file) throws MavenFilteringException {
        try (FileWriter f = new FileWriter(file)) {
            TreeMap<String, Object> sortedByKeyMap = new TreeMap<>();
            properties.entrySet().forEach(e -> sortedByKeyMap.put((String) e.getKey(), e.getValue()));
            final Configuration configuration = new MapConfiguration(sortedByKeyMap);
            PropertiesConfiguration p = new PropertiesConfiguration();
            PropertiesConfigurationLayout layout = new PropertiesConfigurationLayout();
            layout.setGlobalSeparator("=");
            p.setLayout(layout);
            p.copy(configuration);
            p.write(f);
        } catch (ConfigurationException | IOException e) {
            throw new MavenFilteringException(e.getMessage(), e);
        }
    }

    /**
     * Merge the source as a Properties file into outputProperties.
     *
     * @param properties the Properties to merge into
     * @param source the source file to read Properties from
     * @param filtering true if the filterWrappers should be applied
     * @param filterWrappers the FilterWrappers to use
     * @param encoding the encoding to use when filtering
     * @param overwrite true if existing properties should be overwritten. If false, duplicate properties is a build
     * error
     * @throws MavenFilteringException indicating failure
     */
    private void mergeProperties(Properties properties, File source, boolean filtering,
        List<FilterWrapper> filterWrappers, String encoding, boolean overwrite) throws MavenFilteringException {

        Properties p = getFilteredProperties(source, filtering, filterWrappers, encoding);
        for (Entry<Object, Object> entry : p.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            String existing = properties.getProperty(key);
            if (existing != null) {
                if (overwrite) {
                    properties.setProperty(key, value);
                    getLogger().info("Overwriting existing Property '" + key + "' (existing value is '" + existing
                        + "', new value is '" + value + "') while merging source: " + source);
                } else {
                    throw new MavenFilteringException("Property '" + key + "' already exists (existing value is '"
                        + existing + "', new value is '" + value + "') while merging source: " + source);
                }
            }
            properties.setProperty(key, value);
        }
    }

    /**
     * Load and filter properties.
     *
     * @param source the source file to read Properties from
     * @param filtering true if the filterWrappers should be applied
     * @param filterWrappers the FilterWrappers to use
     * @param encoding the encoding to use when filtering
     * @return filtered Properties
     * @throws MavenFilteringException indicating failure
     */
    private Properties getFilteredProperties(File source, boolean filtering, List<FilterWrapper> filterWrappers,
        String encoding) throws MavenFilteringException {

        Properties p = new Properties();
        Reader r = null;
        try (InputStream is = new FileInputStream(source)) {
            r = new InputStreamReader(is, encoding);
            if (filtering) {
                for (FilterWrapper fw : filterWrappers) {
                    r = fw.getReader(r);
                }
            }
            p.load(r);
        } catch (IOException e) {
            throw new MavenFilteringException(e.getMessage(), e);
        } finally {
            IOUtil.close(r);
        }
        return p;
    }

    /**
     * Gets the outputFile property value.
     *
     * @return the current value of the outputFile property
     */
    public String getOutputFile() {
        return outputFile;
    }

    /**
     * Sets the outputFile property.
     *
     * @param outputFile the new property value
     */
    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }

    /**
     * Determine if any duplicate properties should be overwritten or fail the build.
     * <p>
     * Default value is false.
     *
     * @param overwriteProperties true if duplicate properties should be overwritten.
     */
    public void setOverwriteProperties(boolean overwriteProperties) {
        this.overwriteProperties = overwriteProperties;
    }

    /**
     * Gets the overwriteProperties property value.
     *
     * @return the current value of the overwriteProperties property
     */
    public boolean isOverwriteProperties() {
        return overwriteProperties;
    }

    /**
     * Sets the buildContext property.
     *
     * @param buildContext the new property value
     */
    public void setBuildContext(BuildContext buildContext) {
        this.buildContext = buildContext;
    }
}
