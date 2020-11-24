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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link MergePropertiesMojo} class.
 */
public class MergePropertiesMojoTest {

    private MergePropertiesMojo mojo;

    @BeforeEach
    public void setUp() {
        mojo = new MergePropertiesMojo();
        MergeProperitesMavenResourcesFiltering filtering = new MergeProperitesMavenResourcesFiltering();
        filtering.enableLogging(new SilentLog());
        mojo.setMavenResourcesFiltering(filtering);

        mojo.setProject(new MavenProject());
        mojo.setLog(new SilentLog());
    }

    @Test
    public void testDryRun() throws Exception {
        mojo.execute();
        assertFalse(mojo.getMavenResourcesFiltering().isOverwriteProperties());
    }

    @Test
    public void testOverwriteProperties() throws Exception {
        mojo.setOverwriteProperties(true);
        mojo.execute();
        assertTrue(mojo.getMavenResourcesFiltering().isOverwriteProperties());
    }
}
