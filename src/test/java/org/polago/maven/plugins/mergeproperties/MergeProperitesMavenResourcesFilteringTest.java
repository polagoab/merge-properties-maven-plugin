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

package org.polago.maven.plugins.mergeproperties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.utils.io.FileUtils.FilterWrapper;
import org.codehaus.plexus.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;

/**
 * Tests the {@link MergeProperitesMavenResourcesFiltering} class.
 */
public class MergeProperitesMavenResourcesFilteringTest {

    static class TestMergeProperitesMavenResourcesFiltering extends MergeProperitesMavenResourcesFiltering {

        Properties storedProperties = null;

        File storedFile = null;

        /**
         * {@inheritDoc}
         */
        @Override
        protected void storeProperties(Properties properties, File file) throws MavenFilteringException {
            storedProperties = properties;
            storedFile = file;
        }

    };

    static class TestFilterWrapper extends FilterWrapper {
        boolean called = false;

        @Override
        public Reader getReader(Reader fileReader) {
            called = true;
            return fileReader;
        }

    };

    static class TestBuildContext extends DefaultBuildContext {
        boolean isIncremental = false;

        boolean hasDelta = true;

        boolean ignoreDelta;

        /**
         * {@inheritDoc}
         */
        @Override
        public Scanner newScanner(File basedir, boolean ignoreDelta) {
            this.ignoreDelta = ignoreDelta;
            return super.newScanner(basedir, ignoreDelta);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isIncremental() {
            return isIncremental;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasDelta(String relpath) {
            return hasDelta;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean hasDelta(@SuppressWarnings("rawtypes") List relpaths) {
            return hasDelta;
        }
    }

    private TestMergeProperitesMavenResourcesFiltering filtering;

    private final String outputFile = "out.properties";

    private final File outputDirectory = new File("target");

    private final File sourceDirectory = new File("src/test/resources");

    private final TestBuildContext buildContext = new TestBuildContext();

    private TestFilterWrapper filterWrapper;

    private ArrayList<FilterWrapper> filterWrappers;

    @Before
    public void setUp() {
        filtering = new TestMergeProperitesMavenResourcesFiltering();
        filtering.enableLogging(new SilentLog());
        filtering.setOutputFile(outputFile);
        filtering.setBuildContext(buildContext);
        filtering.setOverwriteProperties(true);

        filterWrapper = new TestFilterWrapper();
        filterWrappers = new ArrayList<FilterWrapper>();
        filterWrappers.add(filterWrapper);
    }

    @Test
    public void testFilteringNoResources() throws MavenFilteringException, IOException {

        MavenResourcesExecution execution = new MavenResourcesExecution();
        execution.setOutputDirectory(outputDirectory);
        execution.setEncoding("UTF-8");
        filtering.filterResources(execution);

        assertNull(filtering.storedProperties);
        assertNull(filtering.storedFile);
    }

    @Test
    public void testFilteringResources() throws MavenFilteringException, IOException {

        List<Resource> resources = new ArrayList<Resource>();
        Resource resource = new Resource();
        resource.setDirectory(sourceDirectory.getPath());
        resource.setFiltering(true);
        resources.add(resource);

        MavenResourcesExecution execution = new MavenResourcesExecution();
        execution.setResources(resources);
        execution.setOutputDirectory(outputDirectory);
        execution.setEncoding("UTF-8");
        execution.setFilterWrappers(filterWrappers);

        filtering.filterResources(execution);

        assertNotNull(filtering.storedProperties);
        assertNotNull(filtering.storedFile);

        assertEquals(4, filtering.storedProperties.size());
        assertTrue(filterWrapper.called);
    }

    @Test
    public void testFilteringResourcesNoFiltering() throws MavenFilteringException, IOException {

        List<Resource> resources = new ArrayList<Resource>();
        Resource resource = new Resource();
        resource.setDirectory(sourceDirectory.getPath());
        resource.setFiltering(false);
        resources.add(resource);

        MavenResourcesExecution execution = new MavenResourcesExecution();
        execution.setResources(resources);
        execution.setOutputDirectory(outputDirectory);
        execution.setEncoding("UTF-8");
        execution.setFilterWrappers(filterWrappers);

        filtering.filterResources(execution);

        assertNotNull(filtering.storedProperties);
        assertNotNull(filtering.storedFile);

        assertEquals(4, filtering.storedProperties.size());
        assertFalse(filterWrapper.called);
    }

    @Test
    public void testFilteringResourcesNotOverride() throws MavenFilteringException, IOException {

        List<Resource> resources = new ArrayList<Resource>();
        Resource resource = new Resource();
        resource.setDirectory(sourceDirectory.getPath());
        resource.setFiltering(true);
        resources.add(resource);

        MavenResourcesExecution execution = new MavenResourcesExecution();
        execution.setResources(resources);
        execution.setOutputDirectory(outputDirectory);
        execution.setEncoding("UTF-8");
        execution.setFilterWrappers(filterWrappers);

        filtering.setOverwriteProperties(false);
        try {
            filtering.filterResources(execution);
            fail();
        } catch (MavenFilteringException e) {
            // OK
        }
    }

    @Test
    public void testIncrementalFilteringResources() throws MavenFilteringException, IOException {
        buildContext.isIncremental = true;
        buildContext.hasDelta = false;

        List<Resource> resources = new ArrayList<Resource>();
        Resource resource = new Resource();
        resource.setDirectory(sourceDirectory.getPath());
        resource.setFiltering(true);
        resources.add(resource);

        MavenResourcesExecution execution = new MavenResourcesExecution();
        execution.setResources(resources);
        execution.setOutputDirectory(outputDirectory);
        execution.setEncoding("UTF-8");
        execution.setFilterWrappers(filterWrappers);

        filtering.filterResources(execution);

        assertNotNull(filtering.storedProperties);
        assertNotNull(filtering.storedFile);

        assertEquals(true, buildContext.ignoreDelta);
        assertEquals(4, filtering.storedProperties.size());
        assertTrue(filterWrapper.called);
    }

    @Test
    public void testOverwriteIncrementalFilteringResources() throws MavenFilteringException, IOException {
        buildContext.isIncremental = false;
        buildContext.hasDelta = false;

        List<Resource> resources = new ArrayList<Resource>();
        Resource resource = new Resource();
        resource.setDirectory(sourceDirectory.getPath());
        resource.setFiltering(true);
        resources.add(resource);

        MavenResourcesExecution execution = new MavenResourcesExecution();
        execution.setResources(resources);
        execution.setOutputDirectory(outputDirectory);
        execution.setEncoding("UTF-8");
        execution.setFilterWrappers(filterWrappers);
        execution.setOverwrite(true);

        filtering.filterResources(execution);

        assertNotNull(filtering.storedProperties);
        assertNotNull(filtering.storedFile);

        assertEquals(true, buildContext.ignoreDelta);
        assertEquals(4, filtering.storedProperties.size());
        assertTrue(filterWrapper.called);
    }

}
